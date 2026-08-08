package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.repository.ProjectTeamReferenceRepository;
import org.springframework.stereotype.Repository;

// domain의 ProjectTeamReferenceRepository 계약을 JPA로 구현하는 어댑터.
@Repository
public class ProjectTeamReferenceRepositoryAdapter implements ProjectTeamReferenceRepository {

    private final SpringDataCapProjectTeamReferenceRepository springDataCapProjectTeamReferenceRepository;

    public ProjectTeamReferenceRepositoryAdapter(
            SpringDataCapProjectTeamReferenceRepository springDataCapProjectTeamReferenceRepository) {
        this.springDataCapProjectTeamReferenceRepository = springDataCapProjectTeamReferenceRepository;
    }

    @Override
    public boolean isTeamAssignedToProject(Long projectId, Long teamId) {
        return springDataCapProjectTeamReferenceRepository.existsByIdProjectIdAndIdTeamId(projectId, teamId);
    }
}
