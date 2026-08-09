package com.module06.backend.project.application.command;

/* comment.
    첨부파일 업로드 URL 발급 명령. shape 검증(파일명 비어있지 않음, 크기>0)은 서비스가 한다.
    companyId·projectId는 IDOR 방지용 — 남의 회사 프로젝트 앞으로 업로드 URL을 뽑지 못하도록
    서비스가 검증한다(ConfirmAttachmentCommand와 같은 이유). 둘 다 토큰·경로에서 오고,
    클라이언트가 헤더로 적어 보내는 값이 아니다.
*/
public record IssueAttachmentUploadUrlCommand(Long companyId, Long projectId, String fileName, long fileSize) {
}
