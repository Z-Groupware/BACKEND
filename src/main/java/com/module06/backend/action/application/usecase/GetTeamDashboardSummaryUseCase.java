package com.module06.backend.action.application.usecase;

/* comment.
    2026-08-11, 이슈 #352 — 팀 대시보드 KPI 카드 4종. 전부 action(C) 소유 데이터라 identity/leave
    Port 요청 없이 이 도메인 안에서 완결된다.

    teamActionCount: 이 팀의 TEAM 액션 중 진행 중(IN_PROGRESS)인 것.
    teamMemberActionCount: 이 팀 소속 PERSONAL 액션 전체(상태 무관) — "팀 액션 기준" 라벨,
        팀장이 하달한 팀 액션 아래 흩어진 팀원 개인 액션 총량을 보여주는 카드.
    myActionCount: 호출자 본인의 처리 예정(TODO+IN_PROGRESS) PERSONAL 액션.
    completedActionCount: 호출자 본인의 완료(DONE) PERSONAL 액션 누적.

    연결된 클래스
    - TeamActionService     : 구현체 (application.service)
    - TeamActionController  : 호출자 (presentation)
*/
public interface GetTeamDashboardSummaryUseCase {

    TeamDashboardSummary getTeamDashboardSummary(Long teamId, Long requesterId);

    record TeamDashboardSummary(
            long teamActionCount, long teamMemberActionCount, long myActionCount, long completedActionCount) {
    }
}
