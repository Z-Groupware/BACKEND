package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

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

    /**
     * 역할 CRUD(§6-11·6-12)의 편집 대상 확인. 위 {@code existsBy...} 와 조건은 같지만 행이 필요하다 —
     * 이름을 바꿀 때 "지금 이름과 같은지"(자기 자신과의 중복 제외)를 봐야 한다.
     */
    Optional<RoleWriteEntity> findByIdAndCompanyIdAndTeamId(Long id, Long companyId, Long teamId);

    /**
     * 같은 부서 안 이름 중복 검사(§6-10). 비교는 컬럼 콜레이션({@code utf8mb4_unicode_ci})을 따라
     * 대소문자와 끝 공백을 무시한다 — 온보딩의 이름 중복 검사가 {@code strip().toLowerCase()} 로
     * 키를 맞추는 것과 같은 이유로, 호출자는 앞뒤 공백만 정리해 넘기면 된다.
     */
    // teamId 로 이미 회사가 좁혀진다 — 호출자(TeamRoleService)가 그 부서가 이 회사 것인지
    // 먼저 확인한 뒤에만 부른다. team 은 한 회사에만 속한다 — TENANT_001 예외
    // nosemgrep: tenant-derived-query-without-company-scope
    boolean existsByTeamIdAndName(Long teamId, String name);
}
