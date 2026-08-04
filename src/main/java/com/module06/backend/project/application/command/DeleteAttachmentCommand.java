package com.module06.backend.project.application.command;

/* comment.
    첨부파일 삭제 명령. 삭제 권한은 업로더 본인만(2026-08-05 축소 결정, LEADER+는 JWT 이후).
*/
public record DeleteAttachmentCommand(Long attachmentId, Long requesterId) {
}
