package com.module06.backend.project.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.application.command.ConfirmAttachmentCommand;
import com.module06.backend.project.application.command.DeleteAttachmentCommand;
import com.module06.backend.project.application.command.IssueAttachmentUploadUrlCommand;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedUploadUrl;
import com.module06.backend.project.application.usecase.ConfirmAttachmentUseCase;
import com.module06.backend.project.application.usecase.DeleteAttachmentUseCase;
import com.module06.backend.project.application.usecase.IssueAttachmentUploadUrlUseCase;
import com.module06.backend.project.domain.model.ProjectAttachment;
import com.module06.backend.project.domain.repository.ProjectAttachmentRepository;
import com.module06.backend.project.domain.repository.ProjectRepository;
import com.module06.backend.project.exception.ProjectErrorCode;

import lombok.RequiredArgsConstructor;

/* comment.
    프로젝트 첨부파일 리소스(FR-PJ-08)를 다루는 단일 구현체 — 08/04 팀 협의로 ProjectService와
    같은 이유로 UseCase별 파편화 대신 이 클래스 하나로 묶었다.
    흐름: issueUploadUrl로 URL 발급 → 클라이언트 업로드 → confirm으로 메타데이터 확정.
*/
@Service
@RequiredArgsConstructor
public class ProjectAttachmentService implements
        IssueAttachmentUploadUrlUseCase,
        ConfirmAttachmentUseCase,
        DeleteAttachmentUseCase {

    private final ProjectAttachmentRepository projectAttachmentRepository;
    private final ProjectAttachmentStoragePort projectAttachmentStoragePort;
    private final ProjectRepository projectRepository;

    @Override
    public IssuedUploadUrl issueUploadUrl(IssueAttachmentUploadUrlCommand command) {
        if (command.fileName() == null || command.fileName().isBlank()) {
            throw new IllegalArgumentException("fileName은 비어있을 수 없습니다.");
        }
        if (command.fileSize() <= 0) {
            throw new IllegalArgumentException("fileSize는 0보다 커야 합니다.");
        }
        requireProjectInCompany(command.companyId(), command.projectId());

        String s3Key = buildS3Key(command.companyId(), command.projectId(), command.fileName());
        return projectAttachmentStoragePort.issueUploadUrl(s3Key, command.fileSize());
    }

    /* project-attachments/company-{companyId}/project-{projectId}/{uuid}-{fileName} — CapObjectStoragePort의
       buildS3Key(CaptureUploadService)와 같은 이유로 회사·프로젝트 접두를 둔다: 운영 버킷이 company/CAP과
       공유라 접두가 없으면 서로 다른 도메인 오브젝트가 한 네임스페이스에 섞인다. UUID는 동일 파일명
       재업로드 시 키 충돌(덮어쓰기)을 막는다. */
    private String buildS3Key(Long companyId, Long projectId, String fileName) {
        return "project-attachments/company-%d/project-%d/%s-%s"
                .formatted(companyId, projectId, UUID.randomUUID(), fileName);
    }

    @Override
    @Transactional
    public ProjectAttachment confirm(ConfirmAttachmentCommand command) {
        requireProjectInCompany(command.companyId(), command.projectId());

        return projectAttachmentRepository.findByProjectIdAndFileUrl(command.projectId(), command.fileUrl())
                .orElseGet(() -> {
                    ProjectAttachment attachment = ProjectAttachment.create(
                            command.projectId(),
                            command.fileName(),
                            command.fileUrl(),
                            command.fileSize(),
                            command.uploadedBy()
                    );
                    return projectAttachmentRepository.save(attachment);
                });
    }

    @Override
    @Transactional
    public void delete(DeleteAttachmentCommand command) {
        requireProjectInCompany(command.companyId(), command.projectId());

        ProjectAttachment attachment = projectAttachmentRepository.findById(command.attachmentId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ATTACHMENT_NOT_FOUND));

        /*
            경로의 프로젝트에 속하지 않은 첨부는 "없는 것"으로 답한다. 403 으로 구분해 주면
            남의 회사 첨부 id 를 하나씩 넣어보는 것만으로 존재 여부가 새어 나간다.
        */
        if (!command.projectId().equals(attachment.getProjectId())) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_NOT_FOUND);
        }
        if (!attachment.isUploadedBy(command.requesterId())) {
            throw new BusinessException(ProjectErrorCode.NOT_ATTACHMENT_UPLOADER);
        }

        projectAttachmentRepository.deleteById(command.attachmentId());
        projectAttachmentStoragePort.deleteObject(attachment.getFileUrl());
    }

    /* 타 회사 프로젝트는 403 이 아니라 404 다 — 403 은 "그 id 는 존재한다"를 알려준다. */
    private void requireProjectInCompany(Long companyId, Long projectId) {
        if (!projectRepository.existsActiveByCompanyIdAndId(companyId, projectId)) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
