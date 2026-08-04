package com.module06.backend.project.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    project_team 테이블용 Spring Data JPA 인터페이스(신규, 스캐폴딩에 없던 파일).
*/
public interface SpringDataProjectTeamRepository extends JpaRepository<ProjectTeamJpaEntity, ProjectTeamId> {

    List<ProjectTeamJpaEntity> findAllById_ProjectId(Long projectId);

    void deleteAllById_ProjectId(Long projectId);
}
