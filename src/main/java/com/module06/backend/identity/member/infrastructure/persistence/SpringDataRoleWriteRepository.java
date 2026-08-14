package com.module06.backend.identity.member.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRoleWriteRepository extends JpaRepository<RoleWriteEntity, Long> {

    /**
     * §5-1·§7-4 역할 지정 검증. 회사와 부서를 모두 건다 — 회사 조건만으로는 다른 부서의 역할이
     * 통과하고, 그러면 조직도에서 그 사원이 자기 부서에 없는 역할로 묶인다.
     *
     * <p>이름이 아니라 id 로 확인하는 것이 요점이다. {@code role} 에는 (company_id, name) UNIQUE 가
     * 없어 같은 이름이 두 부서에 하나씩 있을 수 있고, 이름으로 찾으면 화면이 고른 역할과 저장되는
     * 행이 갈린다(2026-08-14 id 기준으로 전환).
     */
    boolean existsByIdAndCompanyIdAndTeamId(Long id, Long companyId, Long teamId);
}
