package com.module06.backend.project.application.command;

/* comment.
    첨부파일 다운로드 URL 발급 명령(2026-08-10, 이홍근 요청). 삭제와 달리 요청자 식별자는
    필요 없다 — 다운로드는 업로더 본인이 아니라 전 구성원 공개다(DeleteAttachmentCommand의
    requesterId와 대비되는 지점). companyId·projectId는 DeleteAttachmentCommand와 같은 이유로
    쓴다 — 경로의 프로젝트가 내 회사 것인지, 그 첨부가 정말 그 프로젝트 소속인지 확인.
*/
public record IssueAttachmentDownloadUrlCommand(Long companyId, Long projectId, Long attachmentId) {
}
