package com.module06.backend.project.application.usecase;

import com.module06.backend.project.application.command.IssueAttachmentDownloadUrlCommand;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedDownloadUrl;

/* comment.
    FR-PJ-08 후속(2026-08-10) — 첨부파일 다운로드 URL 발급 기능 계약. 업로드는 되는데
    다운로드할 방법이 없던 구멍을 메운다(이홍근 요청). 전 구성원 공개 — 업로더 본인 제한 없음.
*/
public interface IssueAttachmentDownloadUrlUseCase {

    IssuedDownloadUrl issueDownloadUrl(IssueAttachmentDownloadUrlCommand command);
}
