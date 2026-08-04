package com.module06.backend.action.presentation.api;

/* comment.
    FR-AC-01,02,03,04,05,09 — 개인 액션·체크리스트·회의별 조회 API 진입점.
    담당 엔드포인트
    - POST   /api/actions                                수동 추가 (예외 경로, MEMBER+)
    - GET    /api/actions                                내 액션 목록 조회 (호출자 본인 소유분만)
    - GET    /api/actions/{actionId}                     상세 조회 (전 구성원)
    - PATCH  /api/actions/{actionId}                     상태 변경 (담당자 본인)
    - PATCH  /api/actions/status/bulk                     보드 저장 시 일괄 상태 변경 (담당자 본인)
    - PATCH  /api/actions/{actionId}/review               AI 검토 확인·수정 (담당자 본인)
    - POST   /api/actions/{actionId}/checklist            체크리스트 추가 (담당자 본인)
    - PATCH  /api/actions/{actionId}/checklist/{itemId}   체크리스트 수정 (담당자 본인)
    - DELETE /api/actions/{actionId}/checklist/{itemId}   체크리스트 삭제 (담당자 본인)
    - GET    /api/meetings/{meetingId}/actions             회의별 액션 조회 (전 구성원) — 유일하게
      base path가 /api/actions가 아니다. 회의(D) 상세 화면 전용 조회라 회의 리소스 하위에 둔다.
    응답은 ApiResponse, 예외는 BusinessException으로만 낸다 — 개별 try-catch 금지(0절 4항).

    연결된 클래스
    - CreateActionUseCase · GetMyActionsUseCase · GetActionDetailUseCase · UpdateActionStatusUseCase ·
      BulkUpdateActionStatusUseCase · ReviewActionUseCase · GetActionsByMeetingUseCase · ChecklistItemUseCase : 호출 대상
    - CreateActionRequest · UpdateActionStatusRequest · BulkUpdateActionStatusRequest ·
      ReviewActionRequest · CreateChecklistItemRequest · UpdateChecklistItemRequest        : 입력 DTO
    - ActionSummaryResponse · ActionDetailResponse · ChecklistItemResponse                 : 출력 DTO
    - ApiResponse                                                                          : 성공 응답 래퍼
*/
public class ActionController {
}
