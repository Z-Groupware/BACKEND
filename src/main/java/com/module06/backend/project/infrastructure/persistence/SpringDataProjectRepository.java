package com.module06.backend.project.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/* comment.
    project 테이블용 Spring Data JPA 인터페이스. 목록·타임라인 조회는 N+1 취약 지점이라
    필요해지면 fetch join/projection을 나중에 추가한다.

    2026-08-10 필터/정렬 도입(이홍근 요청) — 목록 조회(findAllByCompanyId)는 필터 조합마다
    파생 쿼리를 새로 만들지 않도록 JpaSpecificationExecutor로 전환했다(meeting 도메인의
    기존 패턴과 동일, 신규 @Query 아니라 Gate 1 QUERY_002에 안 걸림).
*/
public interface SpringDataProjectRepository
        extends JpaRepository<ProjectJpaEntity, Long>, JpaSpecificationExecutor<ProjectJpaEntity> {

    boolean existsByTag(String tag);

    Optional<ProjectJpaEntity> findByCompanyIdAndTagAndDeletedAtIsNull(Long companyId, String tag);

    boolean existsByCompanyIdAndIdAndDeletedAtIsNull(Long companyId, Long id);

    // soft-delete 포함 배치조회 — 과거 회의가 참조하는 프로젝트 표시 유지용
    List<ProjectJpaEntity> findAllByCompanyIdAndIdIn(Long companyId, List<Long> ids);

    List<ProjectJpaEntity> findAllByCompanyIdAndCreatedByAndDeletedAtIsNull(Long companyId, Long createdBy);
}
