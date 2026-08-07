package com.module06.backend.identity.member.application.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.application.command.IssueMemberCommand;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * 계정 INSERT 와 팀장 지정을 한 트랜잭션에 넣는 부분만 떼어 놓은 협력자 — {@code CompanyRegistrar}
 * 와 같은 이유다. 같은 빈 안에서 자기 메서드를 부르면 스프링 프록시를 타지 않아
 * {@code @Transactional} 이 아무 일도 하지 않으므로, 빈을 나눠 호출자(MemberDirectoryService)가
 * 트랜잭션 밖에서 메일을 보낼 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
class MemberIssuer {

    private final MemberDirectoryCommandPort commandPort;
    private final TeamRepository teamRepository;

    @Transactional
    Long persist(IssueMemberCommand command, String passwordHash) {
        Long memberId = commandPort.issue(
                command.companyId(), command.teamId(), command.jobPositionId(), command.roleLabel(),
                command.name(), command.email(), passwordHash, command.role());

        if (command.role() == Authority.LEADER) {
            teamRepository.updateLeader(command.teamId(), memberId);
        }
        return memberId;
    }
}
