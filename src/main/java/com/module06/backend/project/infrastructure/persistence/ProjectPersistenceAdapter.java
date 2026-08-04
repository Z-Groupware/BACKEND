package com.module06.backend.project.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.repository.ProjectRepository;

/* comment.
    domain의 ProjectRepository 계약을 JPA로 구현하는 어댑터. 실제 구현은 infrastructure
    계층 차례에 채운다 — 지금은 domain 계층 컴파일을 지키기 위한 임시 스텁이다.
*/
public class ProjectPersistenceAdapter implements ProjectRepository {

    @Override
    public Project save(Project project) {
        throw new UnsupportedOperationException("TODO: infra 계층에서 구현");
    }

    @Override
    public Optional<Project> findById(Long id) {
        throw new UnsupportedOperationException("TODO: infra 계층에서 구현");
    }

    @Override
    public boolean existsByTag(String tag) {
        throw new UnsupportedOperationException("TODO: infra 계층에서 구현");
    }

    @Override
    public List<Project> findAllByCompanyId(Long companyId) {
        throw new UnsupportedOperationException("TODO: infra 계층에서 구현");
    }
}
