package com.module06.backend.action.application.usecase;

import com.module06.backend.action.application.command.BulkUpdateActionStatusCommand;

/* comment.
    FR-AC-03 — 개인 액션 보드 "저장" 버튼의 일괄 상태 변경 기능 계약. All-or-nothing.
    단건 PATCH는 없다 — Figma 확인 결과 상태변경은 보드에서만 일어난다(2026-08-07).

    연결된 클래스
    - BulkUpdateActionStatusCommand    : 입력
    - ActionService : 구현체
    - PersonalActionAssigneeOnlyPolicy : 항목별 권한 검사
    - ActionController                 : 호출자 (presentation)
*/
public interface BulkUpdateActionStatusUseCase {

    void bulkUpdateStatus(BulkUpdateActionStatusCommand command);
}
