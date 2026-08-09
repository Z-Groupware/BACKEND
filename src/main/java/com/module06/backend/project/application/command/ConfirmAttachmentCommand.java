package com.module06.backend.project.application.command;

/* comment.
    업로드 완료 확정 명령. 이 시점에 project_attachment 레코드가 생긴다.
    companyId는 IDOR 방지용 — 타 회사 프로젝트에 파일을 붙이지 못하도록 서비스가 검증한다.
*/
public record ConfirmAttachmentCommand(Long projectId, Long companyId, String fileName, String fileUrl, long fileSize, Long uploadedBy) {
}
