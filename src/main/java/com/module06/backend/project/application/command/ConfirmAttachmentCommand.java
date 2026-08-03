package com.module06.backend.project.application.command;

/* comment.
    업로드 완료 후 첨부파일 메타데이터를 확정 저장하는 명령.
    발급된 URL로 FE가 직접 업로드를 마친 뒤 호출되며, 파일명·URL·크기·업로더 id를 담는다.
    이 시점에 비로소 project_attachment 레코드가 생긴다(발급만으로는 생기지 않는다).

    연결된 클래스
    - ConfirmAttachmentRequest      : 이 명령으로 변환되는 요청 DTO (presentation, 미생성)
    - ConfirmAttachmentUseCase      : 이 명령을 받는 기능 계약
    - ConfirmAttachmentService      : 이 명령을 처리하는 구현체
    - ProjectAttachment             : 확정 저장되는 도메인 모델
*/
public record ConfirmAttachmentCommand() {
}
