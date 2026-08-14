package com.module06.backend.identity.member.application.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.command.IssueMemberCommand;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryQueryPort;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.Plan;
import com.module06.backend.identity.member.domain.policy.SeatLimitPolicy;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * 계정 INSERT 와 팀장 지정을 한 트랜잭션에 넣는 부분만 떼어 놓은 협력자 — {@code CompanyRegistrar}
 * 와 같은 이유다. 같은 빈 안에서 자기 메서드를 부르면 스프링 프록시를 타지 않아
 * {@code @Transactional} 이 아무 일도 하지 않으므로, 빈을 나눠 호출자(MemberDirectoryService)가
 * 트랜잭션 밖에서 메일을 보낼 수 있게 한다.
 *
 * <p>{@link MemberDirectoryService#issue}의 좌석 상한·팀장 중복 검사는 이 트랜잭션 <b>밖</b>에서
 * 먼저 한 번 돈다(빠른 실패, 흔한 경우를 빨리 걸러 비밀번호 생성·해싱을 아낀다). 하지만 그 검사와
 * 이 메서드 사이에 동시 요청이 끼어들 수 있어, 정확성의 최종 방어선은 여기다 — 회사 행에 비관적
 * 잠금을 걸고 같은 검사를 다시 한다. 같은 회사로 오는 다른 발급 요청은 이 트랜잭션이 끝날 때까지
 * 대기한다.
 */
@Component
@RequiredArgsConstructor
class MemberIssuer {

    private final MemberDirectoryCommandPort commandPort;
    private final MemberDirectoryQueryPort queryPort;
    private final TeamRepository teamRepository;
    private final CompanyRepository companyRepository;
    private final SeatLimitPolicy seatLimitPolicy;

    @Transactional
    Long persist(IssueMemberCommand command, String passwordHash) {
        companyRepository.lockForUpdate(command.companyId());

        assertSeatAvailable(command.companyId());
        Team team = teamRepository.findByIdAndCompanyId(command.teamId(), command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));
        if (command.role() == Authority.LEADER && team.leaderMemberId() != null) {
            throw new BusinessException(AuthErrorCode.MEMBER_TEAM_LEADER_ALREADY_EXISTS);
        }
        assertRoleAssignable(command);

        Long memberId = commandPort.issue(
                command.companyId(), command.teamId(), command.jobPositionId(), command.roleId(),
                command.name(), command.email(), passwordHash, command.role());

        if (command.role() == Authority.LEADER) {
            teamRepository.updateLeader(command.teamId(), memberId);
        }
        return memberId;
    }

    /**
     * 역할은 선택 값이라 안 보내면(null) "없음"으로 발급한다. 보냈으면 이 회사·이 부서의 역할이어야
     * 한다 — 남의 회사 역할은 물론이고 같은 회사라도 다른 부서의 역할이 붙으면 조직도에서 그 사원이
     * 자기 부서에 없는 역할로 묶인다. 없는 역할을 조용히 "없음"으로 접지 않는 이유는 §7-4와 같다:
     * 사용자가 고른 역할이 사라진 채 성공 응답이 나간다.
     */
    private void assertRoleAssignable(IssueMemberCommand command) {
        if (command.roleId() == null) {
            return;
        }
        if (!queryPort.existsAssignableRole(command.companyId(), command.teamId(), command.roleId())) {
            throw new BusinessException(AuthErrorCode.MEMBER_ROLE_LABEL_NOT_FOUND);
        }
    }

    private void assertSeatAvailable(Long companyId) {
        Plan plan = queryPort.findActivePlan(companyId).orElse(null);
        int maxSeats = seatLimitPolicy.maxSeats(plan);
        long currentSeats = queryPort.findActiveByCompany(companyId).size();
        if (currentSeats + 1 > maxSeats) {
            throw new BusinessException(AuthErrorCode.MEMBER_SEAT_LIMIT_EXCEEDED);
        }
    }
}
