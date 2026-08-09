package com.module06.backend.project.application.usecase;

import com.module06.backend.project.application.command.IssueAttachmentUploadUrlCommand;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedUploadUrl;

/* comment.
    FR-PJ-08 — 첨부파일 업로드 URL 발급 기능 계약. 이 단계에서 메타데이터는 저장하지 않는다.
*/
public interface IssueAttachmentUploadUrlUseCase {

    IssuedUploadUrl issueUploadUrl(IssueAttachmentUploadUrlCommand command);
}
