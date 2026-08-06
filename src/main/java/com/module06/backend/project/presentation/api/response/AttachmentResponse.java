package com.module06.backend.project.presentation.api.response;

import com.module06.backend.project.domain.model.ProjectAttachment;

/* comment.
    첨부파일 응답 DTO. 담을 값: id·파일명·다운로드 URL·파일 크기·업로드 일시.
    두 곳에서 쓰인다 — 업로드 확정 응답, 그리고 프로젝트 상세 응답의 인라인 목록.
    업로더의 내부 member id는 노출하지 않는다(필요하면 이름으로 치환).

    연결된 클래스
    - ProjectAttachmentController : 업로드 확정 응답으로 내보내는 진입점
    - ProjectDetailResponse       : 이 DTO를 목록으로 품는 상위 응답
    - ConfirmAttachmentService    : 이 DTO를 만드는 구현체
    - ProjectAttachment           : 원본 도메인 모델
*/
public record AttachmentResponse(
        Long attachmentId,
        String fileName,
        String fileUrl,
        long fileSize,
        java.time.LocalDateTime createdAt
) {
    public static AttachmentResponse from(ProjectAttachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getFileUrl(),
                attachment.getFileSize(),
                attachment.getCreatedAt()
        );
    }
}
