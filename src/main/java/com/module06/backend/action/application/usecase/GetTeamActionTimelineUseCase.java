package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-08 — 팀 액션 타임라인(?tab=timeline) 조회 기능 계약. 전 구성원 공개.
    해당 팀 액션(parentActionId)에 속한 팀원들의 개인 액션 목록을 내려준다.

    연결된 클래스
    - ActionRepository       : parentActionId 기준 개인 액션 조회
    - ActionSummaryResponse  : 출력 DTO (presentation)
    - TeamActionController   : 호출자 (presentation)
*/
public interface GetTeamActionTimelineUseCase {
}
