package com.module06.backend.project.application.usecase;

/* comment.
    FR-PJ-08 — 첨부파일 삭제 기능 계약. 메타데이터와 실제 오브젝트를 함께 정리한다.
    삭제 권한은 별도 에러 코드로 분리 예정(초안 단계, 팀 검토 전).

    연결된 클래스
    - DeleteAttachmentCommand       : 입력
    - DeleteAttachmentService       : 구현체
    - ProjectAttachmentRepository   : 메타데이터 삭제
    - ProjectAttachmentStoragePort  : 오브젝트 삭제 위임
*/
public interface DeleteAttachmentUseCase {
}
