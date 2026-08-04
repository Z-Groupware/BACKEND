package com.module06.backend.project.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.module06.backend.project.domain.model.ProjectAttachment;
import com.module06.backend.project.domain.repository.ProjectAttachmentRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 ProjectAttachmentRepository 계약을 JPA로 구현하는 어댑터.
*/
@Component
@RequiredArgsConstructor
public class ProjectAttachmentPersistenceAdapter implements ProjectAttachmentRepository {

    private final SpringDataProjectAttachmentRepository springDataProjectAttachmentRepository;

    @Override
    public ProjectAttachment save(ProjectAttachment attachment) {
        ProjectAttachmentJpaEntity entity = ProjectAttachmentJpaEntity.builder()
                .id(attachment.getId())
                .projectId(attachment.getProjectId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .fileSize(attachment.getFileSize())
                .uploadedBy(attachment.getUploadedBy())
                .build();

        return toDomain(springDataProjectAttachmentRepository.save(entity));
    }

    @Override
    public Optional<ProjectAttachment> findById(Long id) {
        return springDataProjectAttachmentRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ProjectAttachment> findAllByProjectId(Long projectId) {
        return springDataProjectAttachmentRepository.findAllByProjectId(projectId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ProjectAttachment> findByProjectIdAndFileUrl(Long projectId, String fileUrl) {
        return springDataProjectAttachmentRepository.findByProjectIdAndFileUrl(projectId, fileUrl).map(this::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        springDataProjectAttachmentRepository.deleteById(id);
    }

    private ProjectAttachment toDomain(ProjectAttachmentJpaEntity entity) {
        return ProjectAttachment.reconstitute(
                entity.getId(),
                entity.getProjectId(),
                entity.getFileName(),
                entity.getFileUrl(),
                entity.getFileSize(),
                entity.getUploadedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
