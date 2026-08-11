package com.module06.backend.action.application.usecase;

import java.util.List;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;

/* comment.
    FR-AC-02 — 개인 액션 목록 조회 기능 계약. 호출자 본인 소유분만 내려준다.
    Figma 확인 결과(2026-08-07) 목록 카드가 담당자·출처회의·프로젝트태그·소속팀·상위팀액션까지
    보여줘서, Action 하나만으로는 응답을 못 만든다 — 조인된 표시값을 함께 담는
    ActionListItem을 반환한다(ActionReassignPort.HandoverableAction과 같은 이유).

    2026-08-10 페이지네이션 도입(이홍근 요청) — page는 0부터 시작. 캘린더(CalendarQueryService)는
    이 UseCase가 아니라 ActionRepository의 전건 조회 메서드를 직접 쓰므로 영향 없다.

    2026-08-10 필터/정렬 추가(이홍근 요청) — status·overdue는 null이면 필터 안 함.

    2026-08-11 — 팀장이 팀원 관리 화면에서 특정 팀원의 개인 액션을 조회할 수 있게
    targetMemberId(nullable) 추가(이홍근 요청). null이면 지금처럼 호출자 본인 목록.
    값이 있으면 호출자가 LEADER인지(requesterAuthority), 대상이 호출자와 같은 팀
    소속인지(requesterTeamId 대조) 검증 후 그 팀원의 목록으로 대체한다 — 이 화면은
    LEADER 전용으로 확인됨(OWNER 케이스는 스코프 밖, Figma "팀원 관리" 화면이
    "회원 관리"와 별개로 LEADER 사이드바에만 있음), OWNER 분기는 만들지 않는다.

    연결된 클래스
    - ActionRepository          : 조회
    - ActionReferenceRepository : 담당자·소속팀·출처회의 이름 조인, 팀 소속 검증(existsMemberInTeam)
    - ActionSummaryResponse     : 출력 DTO (presentation)
    - ActionController          : 호출자 (presentation)
*/
public interface GetMyActionsUseCase {

    ActionListResult getMyActions(
            Long requesterId, String requesterAuthority, Long requesterTeamId, Long targetMemberId,
            ActionStatus status, Boolean overdue, String sort, String order, int page, int size);

    // parentActionTitle은 상위 팀 액션이 없으면(예외 없이 만들어진 경우는 없지만 방어적으로) null.
    // projectName은 2026-08-11 추가(이홍근 요청, 목록 카드 프로젝트별 그룹핑용) — projectTag와
    // 같은 ProjectReference 배치조회에서 이미 갖고 있던 값을 같이 옮기는 것뿐, 추가 쿼리 없음.
    record ActionListItem(
            Action action,
            String assigneeName,
            String projectTag,
            String projectName,
            String teamName,
            String sourceMeetingTitle,
            String parentActionTitle
    ) {
    }

    record ActionListResult(List<ActionListItem> items, long totalElements) {
    }
}
