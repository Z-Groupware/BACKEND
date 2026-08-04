package com.module06.backend.project.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.module06.backend.project.domain.model.ProjectAttachment;
import com.module06.backend.project.domain.repository.ProjectAttachmentRepository;

/* comment.
    domain의 ProjectAttachmentRepository 계약을 JPA로 구현하는 어댑터. 실제 구현은
    infrastructure 계층 차례에 채운다 — 지금은 domain 계층 컴파일을 지키기 위한 임시 스텁이다.
*/
public class ProjectAttachmentPersistenceAdapter implements ProjectAttachmentRepository {

    @Override
    public ProjectAttachment save(ProjectAttachment attachment) {
        throw new UnsupportedOperationException("TODO: infra 계층에서 구현");
    }

    @Override
    public Optional<ProjectAttachment> findById(Long id) {
        throw new UnsupportedOperationException("TODO: infra 계층에서 구현");
    }

    @Override
    public List<ProjectAttachment> findAllByProjectId(Long projectId) {
        throw new UnsupportedOperationException("TODO: infra 계층에서 구현");
    }

    @Override
    public void deleteById(Long id) {
        throw new UnsupportedOperationException("TODO: infra 계층에서 구현");
    }
}
