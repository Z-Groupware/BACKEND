package com.module06.backend.project.application.usecase;

import com.module06.backend.project.application.command.ConfirmAttachmentCommand;
import com.module06.backend.project.domain.model.ProjectAttachment;

/* comment.
    FR-PJ-08 — 업로드 완료 확정 기능 계약. 발급 → FE 업로드 → 확정 3단 흐름의 마지막 단계.
*/
public interface ConfirmAttachmentUseCase {

    ProjectAttachment confirm(ConfirmAttachmentCommand command);
}
