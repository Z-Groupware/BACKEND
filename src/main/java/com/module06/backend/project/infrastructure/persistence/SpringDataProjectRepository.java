package com.module06.backend.project.infrastructure.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    project 테이블용 Spring Data JPA 인터페이스. 목록·타임라인 조회는 N+1 취약 지점이라
    필요해지면 fetch join/projection을 나중에 추가한다.
*/
public interface SpringDataProjectRepository extends JpaRepository<ProjectJpaEntity, Long> {

    boolean existsByTag(String tag);

    // 기존 findAllByCompanyId는 deletedAt 필터가 빠진 버그였음 — 이름 바꿔서 명시적으로 고침
    // 2026-08-10 페이지네이션 도입 — 조회 조건은 그대로, Pageable만 추가.
    List<ProjectJpaEntity> findAllByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    long countByCompanyIdAndDeletedAtIsNull(Long companyId);

    boolean existsByCompanyIdAndIdAndDeletedAtIsNull(Long companyId, Long id);

    // soft-delete 포함 배치조회 — 과거 회의가 참조하는 프로젝트 표시 유지용
    List<ProjectJpaEntity> findAllByCompanyIdAndIdIn(Long companyId, List<Long> ids);

    List<ProjectJpaEntity> findAllByCompanyIdAndCreatedByAndDeletedAtIsNull(Long companyId, Long createdBy);
}
