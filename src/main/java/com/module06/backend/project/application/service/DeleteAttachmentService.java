package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.DeleteAttachmentUseCase;

/* comment.
    FR-PJ-08 첨부파일 삭제 구현체. 쓰기 트랜잭션 경계를 가진다.
    메타데이터 삭제와 오브젝트 삭제를 함께 처리한다 — 한쪽만 지우면 고아가 남는다.
    Port 호출이 실패했을 때의 보상 처리는 구현 시 결정해야 한다(미결).

    연결된 클래스
    - DeleteAttachmentUseCase      : 구현하는 계약
    - DeleteAttachmentCommand      : 입력
    - ProjectAttachmentRepository  : 메타데이터 삭제
    - ProjectAttachmentStoragePort : 오브젝트 삭제 위임
*/
public class DeleteAttachmentService implements DeleteAttachmentUseCase {
}
