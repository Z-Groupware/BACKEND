package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

interface SpringDataRoleWriteRepository extends JpaRepository<RoleWriteEntity, Long> {

    /**
     * §5-1·§7-4 역할 지정 검증. 회사와 부서를 모두 건다 — 회사 조건만으로는 다른 부서의 역할이
     * 통과하고, 그러면 조직도에서 그 사원이 자기 부서에 없는 역할로 묶인다.
     *
     * <p>이름이 아니라 id 로 확인하는 것이 요점이다. {@code role} 에는 (company_id, name) UNIQUE 가
     * 없어 같은 이름이 두 부서에 하나씩 있을 수 있고, 이름으로 찾으면 화면이 고른 역할과 저장되는
     * 행이 갈린다(2026-08-14 id 기준으로 전환).
     *
     * <p><b>공유 잠금을 잡는다.</b> 이 검사와 {@code member.role_id} 갱신은 같은 트랜잭션 안이지만,
     * 그 사이에 역할 삭제(§6-12)가 끼어들면 구성원이 사라진 역할을 가리키게 된다 — role 은
     * member 에서 FK 로 묶여 있지 않아 데이터베이스가 막아 주지 않는다. 삭제 쪽이 잡는 배타
     * 잠금({@link #findLockedByIdAndCompanyIdAndTeamId})과 맞물려 둘 중 하나가 기다리게 된다:
     * 배정이 먼저면 삭제가 "쓰는 사람이 있다"(409)를 보고, 삭제가 먼저면 배정이 이 조회에서
     * 빈 값을 받아 404 가 된다. 어느 순서든 끊긴 참조가 남지 않는다.
     *
     * <p>{@code exists} 가 아니라 행을 읽는 이유가 이것이다 — 잠금은 읽은 행에만 걸린다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<RoleWriteEntity> findSharedByIdAndCompanyIdAndTeamId(Long id, Long companyId, Long teamId);

    /** 역할 CRUD(§6-11)의 이름 수정 대상 확인 — 회사와 부서가 모두 맞아야 잡힌다. */
    Optional<RoleWriteEntity> findByIdAndCompanyIdAndTeamId(Long id, Long companyId, Long teamId);

    /**
     * 역할 삭제(§6-12) 대상을 배타 잠금과 함께 읽는다. 잠금을 잡은 뒤에 "이 역할인 재직자가
     * 있는지"를 세야 배정과의 경합에서 늦게 온 쪽이 앞선 쪽의 결과를 보게 된다 —
     * {@link #findSharedByIdAndCompanyIdAndTeamId} 주석 참조.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RoleWriteEntity> findLockedByIdAndCompanyIdAndTeamId(Long id, Long companyId, Long teamId);

    /**
     * 같은 부서 안 이름 중복 검사(§6-10). 최종 관문은 {@code UK_ROLE_TEAM_NAME}(V2.3.23)이고
     * 이 검사는 친절한 조기 거절이다 — 사전 검사와 INSERT 사이에 다른 요청이 끼어들 수 있다.
     *
     * <p>비교는 컬럼 콜레이션({@code utf8mb4_unicode_ci})을 따라 대소문자와 끝 공백을 무시한다.
     * 제약도 같은 콜레이션을 쓰므로 두 관문이 같은 기준으로 돈다 — 호출자는 앞뒤 공백만
     * 정리해 넘기면 된다.
     */
    // teamId 로 이미 회사가 좁혀진다 — 호출자(TeamRoleService)가 그 부서가 이 회사 것인지
    // 먼저 확인한 뒤에만 부른다. team 은 한 회사에만 속한다 — TENANT_001 예외
    // nosemgrep: tenant-derived-query-without-company-scope
    boolean existsByTeamIdAndName(Long teamId, String name);
}
