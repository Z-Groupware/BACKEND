package com.module06.backend.project.application.usecase;

/* comment.
    FR-PJ-08 — 업로드 완료 확정 기능 계약. 이 시점에 첨부파일 메타데이터를 저장한다.
    발급(Issue) → FE 직접 업로드 → 확정(Confirm) 3단 흐름의 마지막 단계다.

    연결된 클래스
    - ConfirmAttachmentCommand      : 입력
    - ProjectAttachmentService : 구현체
    - ProjectAttachment             : 저장되는 도메인 모델
    - ProjectAttachmentRepository   : 저장소 계약
*/
public interface ConfirmAttachmentUseCase {
}
