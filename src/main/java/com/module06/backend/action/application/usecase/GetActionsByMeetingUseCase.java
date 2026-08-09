package com.module06.backend.action.application.usecase;

import java.util.List;

import com.module06.backend.action.domain.model.Action;

/* comment.
    FR-AC-09 — 회의별 액션 조회 기능 계약. 전 구성원 공개.
    회의 상세 화면(D도메인)에서 그 회의로부터 도출된 액션 전체를 actionType으로 구분해 내려준다.
    TEAM은 담당자 개념이 없어 assigneeName이 null, PERSONAL은 팀 개념이 없어 teamName이
    null이다(GetMyActionsUseCase.ActionListItem·TeamActionListItem과 같은 이유).

    연결된 클래스
    - ActionRepository       : source_meeting_id 기준 조회
    - ActionSummaryResponse  : 출력 DTO (presentation)
    - MeetingActionController : 호출자 (presentation)
*/
public interface GetActionsByMeetingUseCase {

    List<MeetingActionItem> getActionsByMeeting(Long companyId, Long sourceMeetingId);

    record MeetingActionItem(Action action, String assigneeName, String teamName) {
    }
}
