package com.module06.backend.project.application.service;

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

    @Override
    public IssuedUploadUrl issueUploadUrl(IssueAttachmentUploadUrlCommand command) {
        if (command.fileName() == null || command.fileName().isBlank()) {
            throw new IllegalArgumentException("fileName은 비어있을 수 없습니다.");
        }
        if (command.fileSize() <= 0) {
            throw new IllegalArgumentException("fileSize는 0보다 커야 합니다.");
        }

        return projectAttachmentStoragePort.issueUploadUrl(command.fileName(), command.fileSize());
    }

    @Override
    @Transactional
    public ProjectAttachment confirm(ConfirmAttachmentCommand command) {
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
        ProjectAttachment attachment = projectAttachmentRepository.findById(command.attachmentId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ATTACHMENT_NOT_FOUND));

        if (!attachment.isUploadedBy(command.requesterId())) {
            throw new BusinessException(ProjectErrorCode.NOT_ATTACHMENT_UPLOADER);
        }

        projectAttachmentRepository.deleteById(command.attachmentId());
        projectAttachmentStoragePort.deleteObject(attachment.getFileUrl());
    }
}
