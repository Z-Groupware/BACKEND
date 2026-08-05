package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberAuthQueryAdapter implements MemberAuthQueryPort {

    private final SpringDataMemberRepository memberRepository;

    @Override
    public Optional<MemberCredentials> findForLogin(Long companyId, String email) {
        return memberRepository.findByCompanyIdAndEmail(companyId, email).map(this::toCredentials);
    }

    private MemberCredentials toCredentials(MemberJpaEntity member) {
        TeamRefEntity team = member.getTeam();
        return new MemberCredentials(
                member.getId(),
                companyIdOf(member),
                member.getPasswordHash(),
                member.getRole(),
                member.isAdmin(),
                team == null ? null : team.getId(),
                member.getDeletedAt() != null);
    }

    /** 회사 자체는 필요 없고 id 만 쓴다 — 지연 프록시에서 식별자만 꺼내 추가 쿼리를 만들지 않는다. */
    private Long companyIdOf(MemberJpaEntity member) {
        return member.getCompany().getId();
    }
}
