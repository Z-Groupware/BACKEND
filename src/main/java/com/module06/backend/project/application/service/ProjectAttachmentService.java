package com.module06.backend.project.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.application.command.ConfirmAttachmentCommand;
import com.module06.backend.project.application.command.DeleteAttachmentCommand;
import com.module06.backend.project.application.command.IssueAttachmentDownloadUrlCommand;
import com.module06.backend.project.application.command.IssueAttachmentUploadUrlCommand;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedDownloadUrl;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedUploadUrl;
import com.module06.backend.project.application.usecase.ConfirmAttachmentUseCase;
import com.module06.backend.project.application.usecase.DeleteAttachmentUseCase;
import com.module06.backend.project.application.usecase.IssueAttachmentDownloadUrlUseCase;
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
        IssueAttachmentDownloadUrlUseCase,
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
        // 2026-08-10, CodeRabbit(#313) 지적 — 검증 안 된 fileName이 그대로 s3Key에 들어가면
        // "report..pdf" 같은 이름이 키에 ".."를 심어, 정상 발급된 자기 키인데도 confirm의
        // requireOwnAttachmentKey가 경로 조작으로 오판해 거부한다(자기 자신을 걸러내는 오탐).
        if (command.fileName().contains("..")) {
            throw new IllegalArgumentException("fileName에 '..'를 포함할 수 없습니다.");
        }
        if (command.fileSize() <= 0) {
            throw new IllegalArgumentException("fileSize는 0보다 커야 합니다.");
        }
        requireProjectInCompany(command.companyId(), command.projectId());

        String s3Key = buildS3Key(command.companyId(), command.projectId(), command.fileName());
        return projectAttachmentStoragePort.issueUploadUrl(s3Key, command.fileSize());
    }

    /* project-attachments/company-{companyId}/project-{projectId}/{uuid}{.ext} — CapObjectStoragePort의
       buildS3Key(CaptureUploadService)와 같은 이유로 회사·프로젝트 접두를 둔다: 운영 버킷이 company/CAP과
       공유라 접두가 없으면 서로 다른 도메인 오브젝트가 한 네임스페이스에 섞인다. UUID는 동일 파일명
       재업로드 시 키 충돌(덮어쓰기)을 막는다.

       원본 파일명은 더 이상 키에 안 넣는다(2026-08-14, 현지님 지적) — 한글·공백이 든 파일명(녹음 앱
       기본 파일명 등)이 그대로 키에 들어가면 presigned PUT의 SigV4 서명이 클라이언트 쪽 URL 인코딩과
       어긋나 403으로 거절된다. 표시용 원본 파일명은 지금처럼 confirm()이 DB(fileName 컬럼)에만 남긴다. */
    private String buildS3Key(Long companyId, Long projectId, String fileName) {
        return "%s%s%s".formatted(s3KeyPrefix(companyId, projectId), UUID.randomUUID(), extensionOf(fileName));
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex) : "";
    }

    private String s3KeyPrefix(Long companyId, Long projectId) {
        return "project-attachments/company-%d/project-%d/".formatted(companyId, projectId);
    }

    @Override
    @Transactional
    public ProjectAttachment confirm(ConfirmAttachmentCommand command) {
        requireProjectInCompany(command.companyId(), command.projectId());
        requireOwnAttachmentKey(command.companyId(), command.projectId(), command.fileUrl());

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

    /*
        2026-08-10, CodeRabbit(#313) 지적 — fileUrl(S3 키)은 클라이언트가 보내는 값이라 검증 없이
        저장하면, 인증된 사용자가 다른 프로젝트나 다른 도메인(cap의 recordings/...)의 실제 키를
        자기 첨부로 확정한 뒤 삭제 API로 그 객체를 지울 수 있다(ManualRecordingService의 s3Key
        접두 검증과 동일한 이유·동일한 방식).
    */
    private void requireOwnAttachmentKey(Long companyId, Long projectId, String fileUrl) {
        String expectedPrefix = s3KeyPrefix(companyId, projectId);
        if (fileUrl == null || fileUrl.contains("..") || !fileUrl.startsWith(expectedPrefix)) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);
        }
    }

    @Override
    @Transactional
    public void delete(DeleteAttachmentCommand command) {
        requireProjectInCompany(command.companyId(), command.projectId());
        ProjectAttachment attachment = requireAttachmentInProject(command.projectId(), command.attachmentId());

        /*
            업로더 검사를 두지 않는다(2026-08-16 정리). 화면 규칙이 정본이다 — WORKFLOW §1은
            "수정·첨부파일 교체는 Owner만"이고, 그 판정은 컨트롤러의 hasRole('OWNER')가 한다.
            여기에 업로더 조건을 함께 두면 두 규칙의 AND가 되어 "Owner인데 남이 올린 파일은
            못 지운다"는, 어느 문서에도 없는 세 번째 규칙이 만들어진다.

            command.requesterId()는 계속 흘러온다 — 권한 판정에는 안 쓰지만 감사 로그의 근거다.
        */

        /*
            2026-08-10, CodeRabbit(#313) 지적 — S3 삭제 후 DB 삭제 순서로 바꿨다. 원래 순서(DB
            먼저)는 DB 커밋 뒤 S3 삭제가 실패하면 메타데이터만 사라지고 실제 오브젝트가 버킷에
            영원히 안 지워진 채 남는다(고아 오브젝트, 아무도 참조 못 해 정리도 안 됨). 반대
            순서면 S3는 지워졌는데 DB 삭제가 실패해도 트랜잭션이 롤백되어 행이 그대로 남고,
            "가리키는 오브젝트가 없는" 상태로만 남는다 — 다음 삭제 재시도로 정리 가능해 더 안전한
            실패 모드다. outbox+재시도로 완전히 원자적으로 만드는 건 이 PR 범위 밖으로 판단
            (인프라 신설이 필요한 별도 작업) — 이 재정렬은 그 전까지의 최소 완화 조치다.
        */
        projectAttachmentStoragePort.deleteObject(attachment.getFileUrl());
        projectAttachmentRepository.deleteById(command.attachmentId());
    }

    // 2026-08-10, 이홍근 요청 — 업로드는 되는데 다운로드할 방법이 없던 구멍. 삭제와 달리
    // 업로더 제한 없음(전 구성원 공개, 컨트롤러의 @PreAuthorize도 isAuthenticated()만).
    @Override
    public IssuedDownloadUrl issueDownloadUrl(IssueAttachmentDownloadUrlCommand command) {
        requireProjectInCompany(command.companyId(), command.projectId());
        ProjectAttachment attachment = requireAttachmentInProject(command.projectId(), command.attachmentId());

        return projectAttachmentStoragePort.issueDownloadUrl(attachment.getFileUrl());
    }

    /*
        경로의 프로젝트에 속하지 않은 첨부는 "없는 것"으로 답한다. 403 으로 구분해 주면
        남의 회사 첨부 id 를 하나씩 넣어보는 것만으로 존재 여부가 새어 나간다.
    */
    private ProjectAttachment requireAttachmentInProject(Long projectId, Long attachmentId) {
        ProjectAttachment attachment = projectAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ATTACHMENT_NOT_FOUND));

        if (!projectId.equals(attachment.getProjectId())) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return attachment;
    }

    /* 타 회사 프로젝트는 403 이 아니라 404 다 — 403 은 "그 id 는 존재한다"를 알려준다. */
    private void requireProjectInCompany(Long companyId, Long projectId) {
        if (!projectRepository.existsActiveByCompanyIdAndId(companyId, projectId)) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
