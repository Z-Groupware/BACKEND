package com.module06.backend.project.application.usecase;

import com.module06.backend.project.application.command.DeleteAttachmentCommand;

/* comment.
    FR-PJ-08 — 첨부파일 삭제 기능 계약. 메타데이터와 실제 오브젝트를 함께 정리한다.
*/
public interface DeleteAttachmentUseCase {

    void delete(DeleteAttachmentCommand command);
}
