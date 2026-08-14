package com.module06.backend.identity.member.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.port.out.AccountMailPort;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.policy.PasswordGenerator;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.command.DeleteMemberCommand;
import com.module06.backend.identity.member.application.command.IssueMemberCommand;
import com.module06.backend.identity.member.application.command.UpdateMemberAdminCommand;
import com.module06.backend.identity.member.application.command.UpdateMemberRoleCommand;
import com.module06.backend.identity.member.application.dto.IssuedMember;
import com.module06.backend.identity.member.application.dto.MemberDetail;
import com.module06.backend.identity.member.application.dto.MemberListFilter;
import com.module06.backend.identity.member.application.dto.MemberListItem;
import com.module06.backend.identity.member.application.dto.MemberPage;
import com.module06.backend.identity.member.application.dto.OrgChartMember;
import com.module06.backend.identity.member.application.dto.OrgChartSubTeam;
import com.module06.backend.identity.member.application.dto.OrgChartTeam;
import com.module06.backend.identity.member.application.dto.TeamLeaderStatus;
import com.module06.backend.identity.member.application.dto.TeamRosterMember;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryQueryPort;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryQueryPort.MemberRow;
import com.module06.backend.identity.member.application.usecase.DeleteMemberUseCase;
import com.module06.backend.identity.member.application.usecase.GetMemberDashboardSummaryUseCase;
import com.module06.backend.identity.member.application.usecase.GetMemberDashboardSummaryUseCase.MemberDashboardSummary;
import com.module06.backend.identity.member.application.usecase.GetMemberDetailUseCase;
import com.module06.backend.identity.member.application.usecase.GetMemberOrgChartUseCase;
import com.module06.backend.identity.member.application.usecase.GetMembersUseCase;
import com.module06.backend.identity.member.application.usecase.GetTeamLeadersStatusUseCase;
import com.module06.backend.identity.member.application.usecase.GetTeamRosterUseCase;
import com.module06.backend.identity.member.application.usecase.IssueMemberUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMemberAdminUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMemberRoleUseCase;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.PendingHandoverType;
import com.module06.backend.identity.position.domain.repository.PositionRepository;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * 구성원 관리 화면(§7)과 계정 발급(§5-1) — Team·Position 도메인의 CRUD 서비스와 같은 결로,
 * 이 서비스가 6개 유스케이스를 전부 구현한다.
 */
@Service
@RequiredArgsConstructor
public class MemberDirectoryService implements GetMembersUseCase, GetMemberOrgChartUseCase,
        GetMemberDetailUseCase, UpdateMemberRoleUseCase, UpdateMemberAdminUseCase, IssueMemberUseCase,
        GetMemberDashboardSummaryUseCase, GetTeamLeadersStatusUseCase, DeleteMemberUseCase,
        GetTeamRosterUseCase {

    private final MemberDirectoryQueryPort queryPort;
    private final MemberDirectoryCommandPort commandPort;
    private final MemberIssuer memberIssuer;
    private final TeamRepository teamRepository;
    private final PositionRepository positionRepository;
    private final CompanyRepository companyRepository;
    private final AccountMailPort accountMailPort;
    private final PasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    @Transactional(readOnly = true)
    public MemberPage getMembers(Long companyId, MemberListFilter filter, String q, int page, int size) {
        List<MemberRow> rows = queryPort.findActiveByCompany(companyId).stream()
                .filter(row -> matchesFilter(row, filter))
                .filter(row -> matchesQuery(row, q))
                .sorted(Comparator.comparing(MemberRow::memberId))
                .toList();

        List<MemberListItem> content = rows.stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toListItem)
                .toList();

        return MemberPage.of(rows.size(), page, size, content);
    }

    /**
     * LEAVE_PENDING·OFFBOARDING_PENDING 은 둘 다 {@code workStatus = WAITING} 이지만,
     * {@code MemberRow.pendingType}(handover 테이블 읽기 전용 참조로 채워짐)으로 어느 쪽 대기인지
     * 가른다. pendingType 이 없는 WAITING(데이터 정합성이 깨진 경우)은 어느 필터에도 걸리지 않는다
     * — 잘못된 쪽에 노출하는 것보다 안전하다.
     */
    private boolean matchesFilter(MemberRow row, MemberListFilter filter) {
        return switch (filter == null ? MemberListFilter.ALL : filter) {
            case ALL -> true;
            case LEAVE_PENDING -> row.status() == MemberStatus.WAITING
                    && row.pendingType() == PendingHandoverType.VACATION;
            case OFFBOARDING_PENDING -> row.status() == MemberStatus.WAITING
                    && row.pendingType() == PendingHandoverType.OFFBOARDING;
        };
    }

    /**
     * 이름·부서·직급·이메일을 본다. roleLabel 은 검색 대상이 아니다(§7-1).
     *
     * <p>이메일을 넣는 이유: 동명이인이 있는 회사에서 이름만으로는 대상을 특정할 수 없고,
     * 관리자가 손에 쥔 유일한 고유값이 이메일이다(계정 발급도 이메일로 한다). 목록 응답에
     * 이미 이메일이 나가는 관리자 전용 화면이라 검색으로 열어도 새로 새는 정보는 없다.
     */
    private boolean matchesQuery(MemberRow row, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(row.name(), needle)
                || containsIgnoreCase(row.teamName(), needle)
                || containsIgnoreCase(row.positionName(), needle)
                || containsIgnoreCase(row.email(), needle);
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrgChartTeam> getOrgChart(Long companyId) {
        List<Team> teams = teamRepository.findByCompanyId(companyId);
        Map<Long, List<MemberRow>> membersByTeam = queryPort.findActiveByCompany(companyId).stream()
                .filter(row -> row.teamId() != null)
                .collect(Collectors.groupingBy(MemberRow::teamId));

        return teams.stream()
                .sorted(Comparator.comparing(Team::id))
                .map(team -> new OrgChartTeam(team.id(), team.name(), toSubTeams(membersByTeam.getOrDefault(team.id(), List.of()))))
                .toList();
    }

    /** Team → SubTeam → Member (§0, 2026-08-07 확정) — SubTeam 은 roleLabel 로 묶은 응답 전용 그룹이다. */
    private List<OrgChartSubTeam> toSubTeams(List<MemberRow> teamMembers) {
        Map<String, List<MemberRow>> byRoleLabel = teamMembers.stream()
                .collect(Collectors.groupingBy(MemberRow::roleLabel));

        return byRoleLabel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(entry -> new OrgChartSubTeam(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(MemberRow::memberId))
                                .map(row -> new OrgChartMember(row.memberId(), row.name(), row.positionName(), row.authority()))
                                .toList()))
                .toList();
    }

    /**
     * 회의 참석자 픽커가 쓰는 내 팀 로스터(2026-08-13, 회의 도메인 요청). 조직도와 달리 전사가 아니라
     * 한 팀만 담는다 — 다른 팀 로스터를 못 보게 하는 것이 요건이라, 응답을 잘라 주는 쪽이 프론트엔드
     * 필터링에 맡기는 것보다 맞다.
     *
     * <p>{@code ACTIVE} 만 남긴다. 휴직자(VACATION)와 대기자(WAITING — 휴직·오프보딩 승인 대기)는
     * 부를 수 없는 사람이라 픽커에 뜨면 안 된다. 아직 {@code deleted_at} 이 찍히기 전인 RESIGNED 행도
     * 같은 조건에서 함께 빠진다.
     *
     * <p>본인도 걸러내지 않는다. 개설자를 뺄지는 화면의 결정이고, 서버가 미리 빼면 프론트엔드가
     * "나"를 참석자로 넣고 싶어도 목록에 없다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TeamRosterMember> getTeamRoster(Long companyId, Long teamId) {
        if (teamId == null) {
            return List.of();
        }
        return queryPort.findActiveByCompany(companyId).stream()
                .filter(row -> teamId.equals(row.teamId()))
                .filter(row -> row.status() == MemberStatus.ACTIVE)
                .sorted(Comparator.comparing(MemberRow::memberId))
                .map(row -> new TeamRosterMember(row.memberId(), row.name()))
                .toList();
    }

    /**
     * 오너 대시보드 KPI 카드 뒤 2개. {@code findActiveByCompany} 가 이미 소프트삭제된 구성원을 뺀
     * 스냅샷이라, 아직 {@code deleted_at} 이 찍히기 전인 RESIGNED 행만 한 번 더 거른다.
     */
    @Override
    @Transactional(readOnly = true)
    public MemberDashboardSummary getDashboardSummary(Long companyId) {
        List<MemberRow> rows = queryPort.findActiveByCompany(companyId);
        long totalMemberCount = rows.stream()
                .filter(row -> row.status() != MemberStatus.RESIGNED)
                .count();
        long onLeaveMemberCount = rows.stream()
                .filter(row -> row.status() == MemberStatus.VACATION)
                .count();
        return new MemberDashboardSummary(totalMemberCount, onLeaveMemberCount);
    }

    /**
     * 팀장 현황(오너 대시보드). 팀이 기준이다 — {@code member.authority} 가 아니라 팀의
     * {@code leaderMemberId} 를 보고 팀마다 한 행씩 만든다. 그래야 "한 팀에 리더는 정확히 1명" 이
     * 응답에서도 지켜진다. 리더가 공석이거나, 지정된 리더가 이미 퇴사해 스냅샷에 없으면 그 팀은 행이 빠진다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TeamLeaderStatus> getTeamLeadersStatus(Long companyId) {
        Map<Long, MemberRow> rowByMemberId = queryPort.findActiveByCompany(companyId).stream()
                .collect(Collectors.toMap(MemberRow::memberId, row -> row));

        return teamRepository.findByCompanyId(companyId).stream()
                .sorted(Comparator.comparing(Team::id))
                .filter(team -> team.leaderMemberId() != null)
                .map(team -> toLeaderStatus(team, rowByMemberId.get(team.leaderMemberId())))
                .filter(Objects::nonNull)
                .toList();
    }

    private TeamLeaderStatus toLeaderStatus(Team team, MemberRow leader) {
        if (leader == null) {
            return null;
        }
        return new TeamLeaderStatus(leader.memberId(), leader.name(), leader.email(),
                team.id(), team.name(), leader.status(), leader.leaveStartDate(), leader.leaveEndDate());
    }

    @Override
    @Transactional(readOnly = true)
    public MemberDetail getDetail(Long companyId, Long memberId) {
        MemberRow row = queryPort.findActiveById(companyId, memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
        return toDetail(row);
    }

    /**
     * 권한 상승 방어선(§7-4): role 검증 → 대상 조회 → 본인·오너 차단 → 직급 회사 스코프 검증
     * 순서로 막는다. 팀장 교체는 대상의 <b>현재 소속 팀</b>(row.teamId)을 기준으로 한다 — 팀장
     * 지정 전용 엔드포인트가 폐기되며(§6-5) teamId 파라미터 자체가 없어졌다.
     */
    @Override
    @Transactional
    public MemberDetail update(UpdateMemberRoleCommand command) {
        assertAssignable(command.role());

        MemberRow target = queryPort.findActiveById(command.companyId(), command.targetMemberId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        if (target.authority() == Authority.OWNER) {
            throw new BusinessException(AuthErrorCode.MEMBER_CANNOT_MODIFY_OWNER);
        }
        if (command.targetMemberId().equals(command.actingMemberId())) {
            throw new BusinessException(AuthErrorCode.MEMBER_CANNOT_MODIFY_SELF);
        }
        positionRepository.findByIdAndCompanyId(command.jobPositionId(), command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.POSITION_NOT_FOUND));
        assertRoleAssignable(command.companyId(), target.teamId(), command.roleId());

        applyLeaderSideEffects(command.companyId(), target, command.role());
        commandPort.updateRoleAndPosition(command.targetMemberId(), command.role(), command.jobPositionId(),
                command.roleId());
        refreshTokenStore.revokeAllByMember(command.targetMemberId());

        return getDetail(command.companyId(), command.targetMemberId());
    }

    /**
     * 역할은 선택 값이라 안 보내면(null) 그대로 둔다. 보냈으면 이 회사·대상의 <b>현재 소속 부서</b>에
     * 있는 역할이어야 한다 — 없는 역할을 조용히 "없음"으로 접으면 사용자가 고른 역할이 사라진 채
     * 200 이 나간다.
     *
     * <p>부서까지 보는 이유: 역할은 부서에 매인 값이라(V2.3.8) 다른 부서의 역할이 붙으면 조직도에서
     * 그 사원이 자기 부서에 없는 역할로 묶인다. 이름이 아니라 id 로 받게 된 뒤(2026-08-14)
     * 이 검사가 가능해졌다 — 이름으로 받던 때는 같은 이름이 두 부서에 있으면 어느 행이 붙을지
     * 자체가 정해지지 않았다.
     *
     * <p>직급 검증과 같은 자리에서, 쓰기 전에 끝낸다. 나중에 확인하면 이미 권한이 바뀐 뒤에 실패해
     * 트랜잭션이 롤백되더라도 팀장 교체 부수효과의 순서 가정이 흐트러진다.
     */
    private void assertRoleAssignable(Long companyId, Long teamId, Long roleId) {
        if (roleId == null) {
            return;
        }
        if (!queryPort.existsAssignableRole(companyId, teamId, roleId)) {
            throw new BusinessException(AuthErrorCode.MEMBER_ROLE_LABEL_NOT_FOUND);
        }
    }

    /**
     * §7 사원 삭제. 물리 삭제하지 않는다 — 상태를 DELETED 로 바꾸고 {@code deleted_at} 을 찍는다.
     *
     * <p>차단 규칙은 §7-4 와 같다. 오너는 지울 수 없고(소유자 이관은 별도 절차다), 본인도 지울 수
     * 없다 — 마지막 관리자가 자기 계정을 닫아 회사가 잠기는 경로를 없앤다.
     *
     * <p>부수효과 두 가지가 §7-4·오프보딩과 같은 이유로 붙는다. 팀장 자리를 비우지 않으면 후임
     * 승급이 {@code MEMBER_TEAM_LEADER_ALREADY_EXISTS} 로 막히고(권한 강등만으로는 부족하다 —
     * 검사가 보는 것은 {@code team.leader_member_id} 다), 갱신표를 폐기하지 않으면 삭제된 계정이
     * 남은 TTL 동안 스스로 액세스 토큰을 다시 받아 간다.
     */
    @Override
    @Transactional
    public void delete(DeleteMemberCommand command) {
        MemberRow target = queryPort.findActiveById(command.companyId(), command.targetMemberId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        if (target.authority() == Authority.OWNER) {
            throw new BusinessException(AuthErrorCode.MEMBER_CANNOT_MODIFY_OWNER);
        }
        if (command.targetMemberId().equals(command.actingMemberId())) {
            throw new BusinessException(AuthErrorCode.MEMBER_CANNOT_MODIFY_SELF);
        }

        commandPort.softDelete(command.targetMemberId());
        teamRepository.findByLeaderMemberId(command.targetMemberId())
                .ifPresent(team -> teamRepository.updateLeader(team.id(), null));
        refreshTokenStore.revokeAllByMember(command.targetMemberId());
    }

    private void applyLeaderSideEffects(Long companyId, MemberRow target, Authority newRole) {
        if (target.teamId() == null) {
            return;
        }
        Team team = teamRepository.findByIdAndCompanyId(target.teamId(), companyId).orElse(null);
        if (team == null) {
            return;
        }

        if (newRole == Authority.LEADER) {
            if (team.leaderMemberId() != null && !team.leaderMemberId().equals(target.memberId())) {
                commandPort.demoteToMember(team.leaderMemberId());
                /* 강등된 구성원의 토큰에는 LEADER 권한이 담겨 있다 — 안 폐기하면 access TTL 동안 살아 있다. */
                refreshTokenStore.revokeAllByMember(team.leaderMemberId());
            }
            teamRepository.updateLeader(team.id(), target.memberId());
        } else if (target.authority() == Authority.LEADER && target.memberId().equals(team.leaderMemberId())) {
            teamRepository.updateLeader(team.id(), null);
        }
    }

    @Override
    @Transactional
    public MemberDetail update(UpdateMemberAdminCommand command) {
        MemberRow target = queryPort.findActiveById(command.companyId(), command.targetMemberId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        if (target.authority() == Authority.OWNER) {
            throw new BusinessException(AuthErrorCode.MEMBER_OWNER_CANNOT_BE_ADMIN);
        }

        commandPort.updateAdmin(command.targetMemberId(), command.isAdmin());
        refreshTokenStore.revokeAllByMember(command.targetMemberId());

        return getDetail(command.companyId(), command.targetMemberId());
    }

    /**
     * 검증(§5-1) 을 전부 트랜잭션 밖에서 끝낸 뒤 {@link MemberIssuer} 가 INSERT 를 맡는다.
     * 메일은 그 뒤, 커밋이 확정된 다음에 나간다 — 메일 서버가 느려도 방금 만든 계정이 롤백되지
     * 않는다(CompanyRegistrationService 와 같은 경계).
     */
    @Override
    public IssuedMember issue(IssueMemberCommand command) {
        assertAssignable(command.role());

        Team team = teamRepository.findByIdAndCompanyId(command.teamId(), command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));
        positionRepository.findByIdAndCompanyId(command.jobPositionId(), command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.POSITION_NOT_FOUND));

        if (queryPort.existsActiveEmail(command.companyId(), command.email())) {
            throw new BusinessException(AuthErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        if (command.role() == Authority.LEADER && team.leaderMemberId() != null) {
            throw new BusinessException(AuthErrorCode.MEMBER_TEAM_LEADER_ALREADY_EXISTS);
        }

        String password = passwordGenerator.generate();
        String passwordHash = passwordEncoder.encode(password);
        Long memberId = memberIssuer.persist(command, passwordHash);

        Company company = companyRepository.findById(command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.COMPANY_CODE_NOT_FOUND));
        accountMailPort.sendAccountIssued(command.email(), company.code(), password);

        MemberRow issued = queryPort.findActiveById(command.companyId(), memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
        return new IssuedMember(issued.memberId(), issued.name(), issued.email(), issued.teamName(),
                issued.positionName(), issued.authority(), issued.isAdmin(), issued.status());
    }

    /** 직급으로도 구성원 상세로도 권한을 OWNER 로 상승시킬 경로를 없앤다(§0-1, §7-4 검증 1). */
    private void assertAssignable(Authority authority) {
        if (authority != Authority.LEADER && authority != Authority.MEMBER) {
            throw new BusinessException(AuthErrorCode.MEMBER_ROLE_NOT_ASSIGNABLE);
        }
    }

    private MemberListItem toListItem(MemberRow row) {
        return new MemberListItem(row.memberId(), row.name(), row.teamName(), row.positionName(),
                row.authority(), row.isAdmin(), row.roleLabel(), row.status(), row.joinedOn());
    }

    private MemberDetail toDetail(MemberRow row) {
        return new MemberDetail(row.memberId(), row.name(), row.teamId(), row.teamName(),
                row.positionId(), row.positionName(), row.authority(), row.isAdmin(),
                row.roleId(), row.roleLabel(),
                row.status(), row.email(), row.joinedOn());
    }
}
