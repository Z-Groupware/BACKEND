package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSubscriptionRepository extends JpaRepository<SubscriptionRefEntity, Long> {

    /**
     * 살아 있는 구독 하나. 회사당 여러 행이 남을 수 있어(해지 이력) 최신 것을 집는다.
     *
     * <p>회원 조회와 한 쿼리로 합치지 않는다 — 구독은 회원이 아니라 회사에 달려 있고, 해지 이력 중
     * ACTIVE 하나를 골라야 해서 단순 연관으로 표현되지 않는다. 쿼리는 두 번이지만 회원 수와 무관하게
     * 고정이라 N+1 이 아니다.
     */
    Optional<SubscriptionRefEntity> findFirstByCompanyIdAndStatusOrderByIdDesc(Long companyId, String status);
}
