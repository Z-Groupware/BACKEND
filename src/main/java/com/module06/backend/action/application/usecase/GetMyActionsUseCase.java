package com.module06.backend.action.application.usecase;

import java.util.List;

import com.module06.backend.action.domain.model.Action;

/* comment.
    FR-AC-02 — 개인 액션 목록 조회 기능 계약. 호출자 본인 소유분만 내려준다.
    Figma 확인 결과(2026-08-07) 목록 카드가 담당자·출처회의·프로젝트태그·소속팀·상위팀액션까지
    보여줘서, Action 하나만으로는 응답을 못 만든다 — 조인된 표시값을 함께 담는
    ActionListItem을 반환한다(ActionReassignPort.HandoverableAction과 같은 이유).

    2026-08-10 페이지네이션 도입(이홍근 요청) — page는 0부터 시작. 캘린더(CalendarQueryService)는
    이 UseCase가 아니라 ActionRepository의 전건 조회 메서드를 직접 쓰므로 영향 없다.

    연결된 클래스
    - ActionRepository          : 조회
    - ActionReferenceRepository : 담당자·소속팀·출처회의 이름 조인
    - ActionSummaryResponse     : 출력 DTO (presentation)
    - ActionController          : 호출자 (presentation)
*/
public interface GetMyActionsUseCase {

    ActionListResult getMyActions(Long assigneeMemberId, int page, int size);

    // parentActionTitle은 상위 팀 액션이 없으면(예외 없이 만들어진 경우는 없지만 방어적으로) null.
    record ActionListItem(
            Action action,
            String assigneeName,
            String projectTag,
            String teamName,
            String sourceMeetingTitle,
            String parentActionTitle
    ) {
    }

    record ActionListResult(List<ActionListItem> items, long totalElements) {
    }
}
