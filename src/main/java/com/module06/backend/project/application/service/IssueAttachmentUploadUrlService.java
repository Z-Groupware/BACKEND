package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.IssueAttachmentUploadUrlUseCase;

/* comment.
    FR-PJ-08 업로드 URL 발급 구현체. shape 검증(파일명·크기)만 하고 Port에 위임한다.
    메타데이터는 아직 저장하지 않는다 — 확정(Confirm) 단계에서 저장한다.

    연결된 클래스
    - IssueAttachmentUploadUrlUseCase : 구현하는 계약
    - IssueAttachmentUploadUrlCommand : 입력
    - ProjectAttachmentStoragePort    : 발급 위임 경계
*/
public class IssueAttachmentUploadUrlService implements IssueAttachmentUploadUrlUseCase {
}
