package com.module06.backend.action.presentation.api;

/* comment.
    FR-AC-06,07,08 — 팀 액션 API 진입점. base path = /api/team/actions.
    담당 엔드포인트
    - GET    /api/team/actions                              팀 액션 목록 (JWT teamId로 스코프된 LEADER 전용)
    - GET    /api/team/actions/{teamActionId}                상세 (전 구성원, 프로젝트 첨부파일 포함)
    - GET    /api/team/actions/{teamActionId}?tab=timeline   하위 개인 액션 타임라인 (전 구성원)
    - PATCH  /api/team/actions/{teamActionId}                상태 변경 (해당 팀 LEADER)
    - PATCH  /api/team/actions/status/bulk                    보드 저장 시 일괄 상태 변경 (해당 팀 LEADER)
    응답은 ApiResponse, 예외는 BusinessException으로만 낸다 — 개별 try-catch 금지(0절 4항).

    연결된 클래스
    - GetTeamActionsUseCase · GetTeamActionDetailUseCase · GetTeamActionTimelineUseCase ·
      UpdateTeamActionStatusUseCase · BulkUpdateTeamActionStatusUseCase : 호출 대상
    - UpdateActionStatusRequest · BulkUpdateActionStatusRequest         : 입력 DTO(개인·팀 공용)
    - ActionSummaryResponse · TeamActionDetailResponse                  : 출력 DTO
    - ApiResponse                                                       : 성공 응답 래퍼
*/
public class TeamActionController {
}
