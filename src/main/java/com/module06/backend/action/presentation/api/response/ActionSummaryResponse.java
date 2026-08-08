package com.module06.backend.action.presentation.api.response;

import java.time.LocalDate;

import com.module06.backend.action.application.usecase.GetActionsByMeetingUseCase.MeetingActionItem;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase.ActionListItem;
import com.module06.backend.action.application.usecase.GetTeamActionTimelineUseCase.TimelineItem;
import com.module06.backend.action.application.usecase.GetTeamActionsUseCase.TeamActionListItem;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;

/* comment.
    액션 목록/카드용 요약 응답 DTO. 개인 액션 목록·팀 액션 목록·타임라인·회의별 조회에서
    공용으로 쓰인다 — actionType(TEAM/PERSONAL) 필드로 화면이 구분해서 렌더링한다.
    담을 값: id·actionType·title·status·dueDate·needsReview·지연 배지(파생값)에 더해
    담당자명·프로젝트태그·소속팀명·출처회의명·상위팀액션(id·제목) — Figma "내 액션" 카드
    확인 결과 추가(2026-08-07). TEAM 액션·수동 생성 등 값이 없는 항목은 null로 내려간다.
    needsReview는 reviewStatus == PENDING 여부다(AI 분배 직후만 참). isDelayed는 저장값이
    아니라 dueDate 기준으로 조회 시 계산된다.

    연결된 클래스
    - ActionController · TeamActionController : 이 DTO를 내보내는 진입점
    - ActionService · TeamActionService        : 이 DTO를 만드는 구현체
    - GetMyActionsUseCase.ActionListItem       : 조인된 표시값을 담아오는 입력 (application.usecase)
*/
public record ActionSummaryResponse(
        Long id,
        ActionType actionType,
        String title,
        ActionStatus status,
        LocalDate dueDate,
        boolean needsReview,
        boolean isDelayed,
        String assigneeName,
        String projectTag,
        String teamName,
        String sourceMeetingTitle,
        Long parentActionId,
        String parentActionTitle
) {

    // 조인 없이 액션 하나만 있을 때(수동 추가 직후 응답 등) — 나머지 표시값은 null.
    public static ActionSummaryResponse from(Action action) {
        return from(action, null, null, null, null, null);
    }

    // FR-AC-02 목록 조회 — ActionService가 배치조회로 채운 조인 값을 그대로 옮겨 담는다.
    public static ActionSummaryResponse from(ActionListItem item) {
        Action action = item.action();
        return from(action, item.assigneeName(), item.projectTag(), item.teamName(),
                item.sourceMeetingTitle(), item.parentActionTitle());
    }

    // FR-AC-06 목록 조회 — TEAM 액션은 담당자·출처회의·상위액션 개념이 없어 전부 null로 내려간다.
    public static ActionSummaryResponse from(TeamActionListItem item) {
        return from(item.action(), null, item.projectTag(), item.teamName(), null, null);
    }

    // FR-AC-08 타임라인 조회 — 이미 팀 액션 상세 화면 안(같은 프로젝트·같은 팀)이라 projectTag·
    // teamName·상위액션 제목은 중복이라 안 싣는다. 상위액션 id는 그대로 둔다(카드 클릭 이동용).
    public static ActionSummaryResponse from(TimelineItem item) {
        return from(item.action(), item.assigneeName(), null, null, null, null);
    }

    // FR-AC-09 회의별 조회 — 이미 회의 상세 화면 안이라 sourceMeetingTitle은 중복이라 안 싣는다.
    // projectTag도 마찬가지 이유로 뺀다. TEAM은 assigneeName, PERSONAL은 teamName이 null로 온다.
    public static ActionSummaryResponse from(MeetingActionItem item) {
        return from(item.action(), item.assigneeName(), null, item.teamName(), null, null);
    }

    private static ActionSummaryResponse from(
            Action action, String assigneeName, String projectTag, String teamName,
            String sourceMeetingTitle, String parentActionTitle
    ) {
        // 지연은 "진행중" 한정 배지다(2026-08-07 재설계) — 할일은 아직 안 늦은 것, 완료는 지연이 아니다.
        LocalDate today = LocalDate.now();
        boolean delayed = action.getStatus() == ActionStatus.IN_PROGRESS
                && action.getDueDate() != null && action.getDueDate().isBefore(today);

        return new ActionSummaryResponse(
                action.getId(),
                action.getActionType(),
                action.getTitle(),
                action.getStatus(),
                action.getDueDate(),
                action.getReviewStatus() == ActionReviewStatus.PENDING,
                delayed,
                assigneeName,
                projectTag,
                teamName,
                sourceMeetingTitle,
                action.getParentActionId(),
                parentActionTitle
        );
    }
}
