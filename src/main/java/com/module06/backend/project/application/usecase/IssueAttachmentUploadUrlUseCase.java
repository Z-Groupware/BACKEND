package com.module06.backend.project.application.usecase;

/* comment.
    FR-PJ-08 — 첨부파일 업로드 URL 발급 기능 계약.
    C는 URL을 직접 만들지 않는다 — storage(F)에 위임하고 받은 값을 그대로 내려준다.
    이 단계에서는 메타데이터를 저장하지 않는다(업로드 실패 시 고아 레코드가 남으므로).

    연결된 클래스
    - IssueAttachmentUploadUrlCommand : 입력
    - ProjectAttachmentService : 구현체
    - ProjectAttachmentStoragePort    : 발급 위임 경계
    - ConfirmAttachmentUseCase        : 업로드 완료 후 이어지는 다음 단계
*/
public interface IssueAttachmentUploadUrlUseCase {
}
