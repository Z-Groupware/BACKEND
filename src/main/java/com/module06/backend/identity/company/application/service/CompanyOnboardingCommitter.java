package com.module06.backend.identity.company.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.command.OnboardCompanyCommand;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.policy.PasswordGenerator;
import com.module06.backend.identity.company.domain.repository.CompanyProfileRepository;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryQueryPort;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.Plan;
import com.module06.backend.identity.member.domain.policy.SeatLimitPolicy;
import com.module06.backend.identity.member.domain.repository.RoleRepository;
import com.module06.backend.identity.position.domain.model.Position;
import com.module06.backend.identity.position.domain.repository.PositionRepository;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * 온보딩 커밋(§4-1) 한 트랜잭션 전용 협력자 — {@code CompanyRegistrar}/{@code MemberIssuer}와
 * 같은 "트랜잭션 전용 협력자" 패턴이다. 같은 빈 안에서 이 메서드를 호출하면 프록시를 타지 않아
 * {@code @Transactional}이 아무 일도 하지 않으므로 별도 빈으로 둔다.
 *
 * <p>비밀번호 평문은 이 메서드가 리턴하는 {@link CommitResult}에만 담긴다 — 호출자가 트랜잭션
 * 커밋 후 메일 발송에 한 번 쓰고 버린다. 회사 행 잠금 하나로 팀·서브팀·직급 생성부터 초대별
 * 계정 발급, {@code company.onboarded_at} 세팅까지 전부 한 트랜잭션 안에서 처리한다 — §5-1처럼
 * 발급마다 따로 잠그지 않는다(온보딩은 최초 1회이므로 동시 호출 자체를 막는 것으로 충분하다).
 */
@Component
@RequiredArgsConstructor
class CompanyOnboardingCommitter {

    private final CompanyRepository companyRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final TeamRepository teamRepository;
    private final RoleRepository roleRepository;
    private final PositionRepository positionRepository;
    private final MemberDirectoryQueryPort memberQueryPort;
    private final MemberDirectoryCommandPort memberCommandPort;
    private final SeatLimitPolicy seatLimitPolicy;
    private final PasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    CommitResult commit(OnboardCompanyCommand command) {
        Long companyId = command.companyId();
        companyRepository.lockForUpdate(companyId);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.COMPANY_CODE_NOT_FOUND));
        if (company.onboardedAt() != null) {
            throw new BusinessException(AuthErrorCode.ALREADY_ONBOARDED);
        }

        assertPositionRolesAssignable(command);

        Map<String, Authority> positionAuthorityByTempId = command.jobPositions().stream()
                .collect(Collectors.toMap(OnboardCompanyCommand.JobPositionNode::tempId,
                        OnboardCompanyCommand.JobPositionNode::defaultRole));

        assertReferencesResolve(command, positionAuthorityByTempId);
        assertSubTeamsBelongToTeam(command);
        assertNoDuplicateTeamLeader(command, positionAuthorityByTempId);

        /*
         * 좌석 검사는 "실제로 발급될 계정 수"를 봐야 한다 — 요청 안 중복·기존 재직자와 겹치는
         * 이메일은 애초에 발급되지 않으므로, invites().size() 를 그대로 쓰면 스킵될 초대까지
         * 좌석을 잡아먹는 것으로 오판해 통과해야 할 온보딩을 거절할 수 있다(코드래빗 지적).
         */
        List<OnboardCompanyCommand.InviteNode> toIssue = new ArrayList<>();
        List<SkippedInvite> skipped = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        for (OnboardCompanyCommand.InviteNode invite : command.invites()) {
            String email = invite.email();
            if (!seenEmails.add(email) || memberQueryPort.existsActiveEmail(companyId, email)) {
                skipped.add(new SkippedInvite(email, "DUPLICATE_EMAIL"));
                continue;
            }
            toIssue.add(invite);
        }
        assertSeatAvailable(companyId, toIssue.size());

        Map<String, Long> teamIdByTempId = new HashMap<>();
        Map<String, Long> subTeamIdByTempId = new HashMap<>();
        int subTeamCount = 0;
        for (OnboardCompanyCommand.TeamNode teamNode : command.teams()) {
            Team team = teamRepository.create(companyId, teamNode.name());
            teamIdByTempId.put(teamNode.tempId(), team.id());
            for (OnboardCompanyCommand.SubTeamNode subTeamNode : teamNode.subTeams()) {
                Long roleId = roleRepository.create(companyId, team.id(), subTeamNode.name());
                subTeamIdByTempId.put(subTeamNode.tempId(), roleId);
                subTeamCount++;
            }
        }

        Map<String, Long> positionIdByTempId = new HashMap<>();
        for (OnboardCompanyCommand.JobPositionNode positionNode : command.jobPositions()) {
            Position position = positionRepository.create(
                    companyId, positionNode.name(), positionNode.defaultRole(), null);
            positionIdByTempId.put(positionNode.tempId(), position.id());
        }

        List<IssuedAccount> issuedAccounts = new ArrayList<>();

        for (OnboardCompanyCommand.InviteNode invite : toIssue) {
            String email = invite.email();
            Long teamId = teamIdByTempId.get(invite.teamTempId());
            Long roleId = invite.subTeamTempId() != null ? subTeamIdByTempId.get(invite.subTeamTempId()) : null;
            Long positionId = positionIdByTempId.get(invite.jobPositionTempId());
            Authority authority = positionAuthorityByTempId.get(invite.jobPositionTempId());

            String password = passwordGenerator.generate();
            String passwordHash = passwordEncoder.encode(password);
            Long memberId = memberCommandPort.issueWithRole(
                    companyId, teamId, positionId, roleId, invite.name(), email, passwordHash, authority);

            if (authority == Authority.LEADER) {
                teamRepository.updateLeader(teamId, memberId);
            }
            if (invite.isAdmin()) {
                memberCommandPort.updateAdmin(memberId, true);
            }

            issuedAccounts.add(new IssuedAccount(email, password));
        }

        LocalDateTime now = LocalDateTime.now();
        companyProfileRepository.markOnboarded(companyId, now);

        return new CommitResult(now, teamIdByTempId.size(), subTeamCount, positionIdByTempId.size(),
                issuedAccounts, skipped);
    }

    /**
     * 직급으로 권한을 상승시킬 경로를 온보딩에도 막는다(§4-1 검증 6).
     *
     * <p>{@code Authority} 는 OWNER 를 포함하는 3값 enum이라 역직렬화만으로는 걸러지지 않는다.
     * §5-1(계정 발급)과 §6-7(직급 CRUD)이 각각 {@code assertAssignable} 로 막고 있지만, 온보딩은
     * 직급과 구성원을 한 번에 만들어 그 두 관문을 모두 우회한다 — 막지 않으면 오너가 온보딩으로
     * OWNER 계정을 여러 개 만들 수 있다("OWNER 는 기업당 1명" 위반).
     */
    private void assertPositionRolesAssignable(OnboardCompanyCommand command) {
        for (OnboardCompanyCommand.JobPositionNode position : command.jobPositions()) {
            if (position.defaultRole() != Authority.LEADER && position.defaultRole() != Authority.MEMBER) {
                throw new BusinessException(AuthErrorCode.POSITION_ROLE_NOT_ASSIGNABLE);
            }
        }
    }

    /** tempId 참조가 요청 안의 다른 배열을 실제로 가리키는지 확인한다 — DB 조회 없이 메모리에서. */
    private void assertReferencesResolve(OnboardCompanyCommand command, Map<String, Authority> positionAuthority) {
        Set<String> teamTempIds = command.teams().stream()
                .map(OnboardCompanyCommand.TeamNode::tempId).collect(Collectors.toSet());
        for (OnboardCompanyCommand.InviteNode invite : command.invites()) {
            if (!teamTempIds.contains(invite.teamTempId())) {
                throw new BusinessException(AuthErrorCode.TEAM_NOT_FOUND);
            }
            if (!positionAuthority.containsKey(invite.jobPositionTempId())) {
                throw new BusinessException(AuthErrorCode.POSITION_NOT_FOUND);
            }
        }
    }

    private void assertSubTeamsBelongToTeam(OnboardCompanyCommand command) {
        for (OnboardCompanyCommand.InviteNode invite : command.invites()) {
            if (invite.subTeamTempId() == null) {
                continue;
            }
            boolean belongs = command.teams().stream()
                    .filter(t -> t.tempId().equals(invite.teamTempId()))
                    .anyMatch(t -> t.subTeams().stream()
                            .anyMatch(s -> s.tempId().equals(invite.subTeamTempId())));
            if (!belongs) {
                throw new BusinessException(AuthErrorCode.SUB_TEAM_NOT_IN_TEAM);
            }
        }
    }

    private void assertNoDuplicateTeamLeader(OnboardCompanyCommand command, Map<String, Authority> positionAuthority) {
        Map<String, Long> leaderCountByTeam = new HashMap<>();
        for (OnboardCompanyCommand.InviteNode invite : command.invites()) {
            if (positionAuthority.get(invite.jobPositionTempId()) != Authority.LEADER) {
                continue;
            }
            long count = leaderCountByTeam.merge(invite.teamTempId(), 1L, Long::sum);
            if (count > 1) {
                throw new BusinessException(AuthErrorCode.TEAM_LEADER_DUPLICATED);
            }
        }
    }

    private void assertSeatAvailable(Long companyId, int inviteCount) {
        Plan plan = memberQueryPort.findActivePlan(companyId).orElse(null);
        int maxSeats = seatLimitPolicy.maxSeats(plan);
        long currentSeats = memberQueryPort.findActiveByCompany(companyId).size();
        if (currentSeats + inviteCount > maxSeats) {
            throw new BusinessException(AuthErrorCode.MEMBER_SEAT_LIMIT_EXCEEDED);
        }
    }

    record CommitResult(LocalDateTime onboardedAt, int teamCount, int subTeamCount, int jobPositionCount,
                         List<IssuedAccount> issuedAccounts, List<SkippedInvite> skipped) {
    }

    record IssuedAccount(String email, String password) {
    }

    record SkippedInvite(String email, String reason) {
    }
}
