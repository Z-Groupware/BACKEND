package com.module06.backend.project.application.command;

/* comment.
    첨부파일 업로드 URL 발급 명령. shape 검증(파일명 비어있지 않음, 크기>0)은 서비스가 한다.
*/
public record IssueAttachmentUploadUrlCommand(String fileName, long fileSize) {
}
