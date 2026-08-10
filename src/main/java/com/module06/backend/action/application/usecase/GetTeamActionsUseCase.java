package com.module06.backend.action.application.usecase;

import java.util.List;

import com.module06.backend.action.domain.model.Action;

/* comment.
    FR-AC-06 — 팀 액션 목록 조회 기능 계약. JWT의 teamId로 자동 스코프된 LEADER 전용
    (Controller의 @PreAuthorize(hasRole)가 1차 방어, teamId 자체가 JWT에서만 나와 다른 팀
    조회가 애초에 불가능하므로 여기서 별도 소유권 검사를 하지 않는다).

    2026-08-10 페이지네이션 도입(이홍근 요청) — page는 0부터 시작.

    연결된 클래스
    - ActionRepository          : 조회
    - ActionReferenceRepository : 프로젝트태그·팀명 조인
    - ActionSummaryResponse     : 출력 DTO (presentation, 개인 액션 목록과 공용)
    - TeamActionController      : 호출자 (presentation)
*/
public interface GetTeamActionsUseCase {

    TeamActionListResult getTeamActions(Long teamId, int page, int size);

    record TeamActionListItem(Action action, String projectTag, String teamName) {
    }

    record TeamActionListResult(List<TeamActionListItem> items, long totalElements) {
    }
}
