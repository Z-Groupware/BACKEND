package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.ConfirmAttachmentUseCase;

/* comment.
    FR-PJ-08 업로드 확정 구현체. 쓰기 트랜잭션 경계를 가진다.
    FE가 발급받은 URL로 업로드를 마친 뒤 호출되며, 이때 첨부파일 메타데이터를 저장한다.

    연결된 클래스
    - ConfirmAttachmentUseCase    : 구현하는 계약
    - ConfirmAttachmentCommand    : 입력
    - ProjectAttachment           : 저장되는 도메인 모델
    - ProjectAttachmentRepository : 저장
*/
public class ConfirmAttachmentService implements ConfirmAttachmentUseCase {
}
