package com.module06.backend.project.application.usecase;

import com.module06.backend.project.application.command.BulkUpdateProjectStatusCommand;

/* comment.
    FR-PJ-06 — OWNER 프로젝트 보드의 상태 일괄 변경 기능 계약. 반환값 없음(FE가 이미 로컬
    상태를 갖고 있어 커밋 성공 여부만 알면 된다 — 실패 시 예외로 표현).
*/
public interface BulkUpdateProjectStatusUseCase {

    void bulkUpdateStatus(BulkUpdateProjectStatusCommand command);
}
