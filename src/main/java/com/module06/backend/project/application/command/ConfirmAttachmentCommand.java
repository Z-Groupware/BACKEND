package com.module06.backend.project.application.command;

/* comment.
    업로드 완료 확정 명령. 이 시점에 project_attachment 레코드가 생긴다.
*/
public record ConfirmAttachmentCommand(Long projectId, String fileName, String fileUrl, long fileSize, Long uploadedBy) {
}
