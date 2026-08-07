package com.module06.backend.identity.member.infrastructure.persistence;

import org.springframework.stereotype.Repository;

import com.module06.backend.identity.company.application.port.out.OwnerAccountPort;
import com.module06.backend.identity.company.infrastructure.persistence.CompanyJpaEntity;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * {@link OwnerAccountPort} 구현. 오너 계정 INSERT 는 member 도메인이 맡는다 —
 * 엔티티와 저장소가 이 패키지에 있고 바깥에 열려 있지 않다.
 */
@Repository
@RequiredArgsConstructor
public class OwnerAccountAdapter implements OwnerAccountPort {

    /** 역할 "없음". 전 회사 공용 시스템 행이다(V2.3.9). */
    private static final long ROLE_NONE_ID = 2L;

    private final SpringDataMemberRepository memberRepository;
    private final EntityManager entityManager;

    @Override
    public Long createOwner(Long companyId, String name, String email, String passwordHash) {
        /*
         * getReference 로 프록시만 잡는다. 연관을 채우려고 회사·역할을 실제로 읽으면 SELECT 가 두 번
         * 더 나가는데, INSERT 에 필요한 것은 FK 값뿐이다.
         */
        CompanyJpaEntity company = entityManager.getReference(CompanyJpaEntity.class, companyId);
        RoleRefEntity noRole = entityManager.getReference(RoleRefEntity.class, ROLE_NONE_ID);

        MemberJpaEntity owner = MemberJpaEntity.owner(company, noRole, name, email, passwordHash);
        return memberRepository.save(owner).getId();
    }
}
