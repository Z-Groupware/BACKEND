package com.module06.backend.project.application.command;

/* comment.
    첨부파일 삭제 명령. 삭제 권한은 업로더 본인만(2026-08-05 축소 결정, LEADER+는 JWT 이후).
    requesterId는 토큰에서 온다 — 헤더로 받으면 실제 업로더의 id만 알면 남의 첨부를 지울 수 있어,
    "업로더 본인만" 규칙이 그대로 무력화된다.
    companyId·projectId는 경로의 프로젝트가 내 회사 것인지, 그 첨부가 정말 그 프로젝트 소속인지
    확인하는 데 쓴다 — 없으면 경로의 projectId가 아무 의미 없는 장식이 된다.
*/
public record DeleteAttachmentCommand(Long companyId, Long projectId, Long attachmentId, Long requesterId) {
}
