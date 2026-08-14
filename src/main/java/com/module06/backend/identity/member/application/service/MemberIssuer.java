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
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * 계정 INSERT 와 팀장 지정을 한 트랜잭션에 넣는 부분만 떼어 놓은 협력자 — {@code CompanyRegistrar}
 * 와 같은 이유다. 같은 빈 안에서 자기 메서드를 부르면 스프링 프록시를 타지 않아
 * {@code @Transactional} 이 아무 일도 하지 않으므로, 빈을 나눠 호출자(MemberDirectoryService)가
 * 트랜잭션 밖에서 메일을 보낼 수 있게 한다.
 *
 * <p>{@link MemberDirectoryService#issue}의 팀장 중복 검사는 이 트랜잭션 <b>밖</b>에서
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

    @Transactional
    Long persist(IssueMemberCommand command, String passwordHash) {
        companyRepository.lockForUpdate(command.companyId());

        Team team = teamRepository.findByIdAndCompanyId(command.teamId(), command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));
        if (command.role() == Authority.LEADER && team.leaderMemberId() != null) {
            throw new BusinessException(AuthErrorCode.MEMBER_TEAM_LEADER_ALREADY_EXISTS);
        }

        Long memberId = commandPort.issue(
                command.companyId(), command.teamId(), command.jobPositionId(), command.roleLabel(),
                command.name(), command.email(), passwordHash, command.role());

        if (command.role() == Authority.LEADER) {
            teamRepository.updateLeader(command.teamId(), memberId);
        }
        return memberId;
    }
}
