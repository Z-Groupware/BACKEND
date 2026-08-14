package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTeamRoleRefRepository extends JpaRepository<TeamRoleRefEntity, Long> {

    /**
     * 이 회사의 역할과 전 회사 공용 시스템 역할을 한 번에 읽는다. {@code company_id IS NULL} 을
     * 같이 거는 이유는 "없음"(V2.3.9)이 특정 회사 소유가 아니면서도 모든 회사의 역할 select 에
     * 올라가야 하기 때문이다 — 조건을 나눠 두 번 조회하면 부서 목록 한 번에 쿼리가 하나 더 붙는다.
     */
    List<TeamRoleRefEntity> findByCompanyIdOrCompanyIdIsNull(Long companyId);
}
