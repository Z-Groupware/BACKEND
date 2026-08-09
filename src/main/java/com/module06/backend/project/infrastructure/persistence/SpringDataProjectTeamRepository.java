package com.module06.backend.project.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    project_team 테이블용 Spring Data JPA 인터페이스(신규, 스캐폴딩에 없던 파일).
*/
public interface SpringDataProjectTeamRepository extends JpaRepository<ProjectTeamJpaEntity, ProjectTeamId> {

    List<ProjectTeamJpaEntity> findAllById_ProjectId(Long projectId);

    // 목록 조회 N+1 방지용 배치 조회(2026-08-09).
    List<ProjectTeamJpaEntity> findAllById_ProjectIdIn(List<Long> projectIds);

    void deleteAllById_ProjectId(Long projectId);
}
