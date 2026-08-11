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
    담을 값: id·actionType·title·status·startDate·dueDate·needsReview·지연 배지(파생값)에
    더해 담당자명·프로젝트태그·소속팀명·출처회의명·상위팀액션(id·제목) — Figma "내 액션" 카드
    확인 결과 추가(2026-08-07). TEAM 액션·수동 생성 등 값이 없는 항목은 null로 내려간다.
    needsReview는 reviewStatus == PENDING 여부다(AI 분배 직후만 참). isDelayed는 저장값이
    아니라 dueDate 기준으로 조회 시 계산된다.

    startDate는 프로젝트 startDate와 동일한 성격의 표시용 값이다(2026-08-10, 이홍근 요청 —
    보드/타임라인이 프로젝트·액션에 같은 로직을 쓰는데 액션만 노출이 빠져있었음). 컬럼 자체는
    이미 존재(V2.6.7, 2026-08-07 재설계로 isDone·startDate에서 status를 파생) — 여기선 그냥
    노출만 추가한다. TODO 상태(아직 시작 안 함)면 null이 정상이다 — 마이그레이션 이전 데이터라서
    비어있는 게 아니라, "아직 시작 안 한 액션"이라는 의미 자체가 null이다.

    2026-08-11 — description·projectId 추가(이홍근 요청, 목록 카드 한 줄 요약·프로젝트별 그룹핑).
    둘 다 Action 엔티티에 이미 있는 값이라 모든 경로에서 조인 없이 채운다. projectName은
    Action에 없어(ProjectReference 조인 필요) 목록 두 경로(개인·팀)에서만 채워지고, 이미
    "회의/타임라인 화면 안이라 중복" 이유로 projectTag를 비우던 두 경로(타임라인·회의별 조회)는
    projectName도 같은 이유로 비운다.

    2026-08-11 — childDoneCount·childTotalCount 추가(이슈 #355, 이홍근 요청). GET /api/team/actions
    (TeamActionListItem 경로)에서만 채워진다 — 하위 개인 액션 진척 게이지("3/5")용. 나머지 경로는
    Integer(boxed) null이다 — "하위 개념 자체가 없음"과 "하위가 0/0으로 비어있음"을 구분해야
    해서 int 0으로 defaulting하지 않는다(PERSONAL 액션·타임라인·회의별 조회는 애초에 하위 개념이
    없다). TeamActionListItem 경로는 서비스가 항상 0/0 이상의 실값을 채워 보낸다.

    연결된 클래스
    - ActionController · TeamActionController : 이 DTO를 내보내는 진입점
    - ActionService · TeamActionService        : 이 DTO를 만드는 구현체
    - GetMyActionsUseCase.ActionListItem       : 조인된 표시값을 담아오는 입력 (application.usecase)
*/
public record ActionSummaryResponse(
        Long id,
        ActionType actionType,
        String title,
        String description,
        ActionStatus status,
        LocalDate startDate,
        LocalDate dueDate,
        boolean needsReview,
        boolean isDelayed,
        String assigneeName,
        Long projectId,
        String projectTag,
        String projectName,
        String teamName,
        String sourceMeetingTitle,
        Long parentActionId,
        String parentActionTitle,
        Integer childDoneCount,
        Integer childTotalCount
) {

    // 조인 없이 액션 하나만 있을 때(수동 추가 직후 응답 등) — 나머지 표시값은 null.
    public static ActionSummaryResponse from(Action action) {
        return from(action, null, null, null, null, null);
    }

    // FR-AC-02 목록 조회 — ActionService가 배치조회로 채운 조인 값을 그대로 옮겨 담는다.
    public static ActionSummaryResponse from(ActionListItem item) {
        Action action = item.action();
        return from(action, item.assigneeName(), item.projectTag(), item.projectName(), item.teamName(),
                item.sourceMeetingTitle(), item.parentActionTitle());
    }

    // FR-AC-06 목록 조회 — TEAM 액션은 담당자·출처회의·상위액션 개념이 없어 전부 null로 내려간다.
    // childDoneCount·childTotalCount는 유일하게 이 경로에서만 실값이다(이슈 #355).
    public static ActionSummaryResponse from(TeamActionListItem item) {
        return from(item.action(), null, item.projectTag(), item.projectName(), item.teamName(), null, null,
                item.childDoneCount(), item.childTotalCount());
    }

    // FR-AC-08 타임라인 조회 — 이미 팀 액션 상세 화면 안(같은 프로젝트·같은 팀)이라 projectTag·
    // projectName·teamName·상위액션 제목은 중복이라 안 싣는다. 상위액션 id는 그대로 둔다(카드 클릭 이동용).
    public static ActionSummaryResponse from(TimelineItem item) {
        return from(item.action(), item.assigneeName(), null, null, null, null);
    }

    // FR-AC-09 회의별 조회 — 이미 회의 상세 화면 안이라 sourceMeetingTitle은 중복이라 안 싣는다.
    // projectTag·projectName도 마찬가지 이유로 뺀다. TEAM은 assigneeName, PERSONAL은 teamName이 null로 온다.
    public static ActionSummaryResponse from(MeetingActionItem item) {
        return from(item.action(), item.assigneeName(), null, item.teamName(), null, null);
    }

    private static ActionSummaryResponse from(
            Action action, String assigneeName, String projectTag, String teamName,
            String sourceMeetingTitle, String parentActionTitle
    ) {
        return from(action, assigneeName, projectTag, null, teamName, sourceMeetingTitle, parentActionTitle);
    }

    private static ActionSummaryResponse from(
            Action action, String assigneeName, String projectTag, String projectName, String teamName,
            String sourceMeetingTitle, String parentActionTitle
    ) {
        return from(action, assigneeName, projectTag, projectName, teamName, sourceMeetingTitle, parentActionTitle, null, null);
    }

    private static ActionSummaryResponse from(
            Action action, String assigneeName, String projectTag, String projectName, String teamName,
            String sourceMeetingTitle, String parentActionTitle, Integer childDoneCount, Integer childTotalCount
    ) {
        // 지연은 "진행중" 한정 배지다(2026-08-07 재설계) — 할일은 아직 안 늦은 것, 완료는 지연이 아니다.
        LocalDate today = LocalDate.now();
        boolean delayed = action.getStatus() == ActionStatus.IN_PROGRESS
                && action.getDueDate() != null && action.getDueDate().isBefore(today);

        return new ActionSummaryResponse(
                action.getId(),
                action.getActionType(),
                action.getTitle(),
                action.getDescription(),
                action.getStatus(),
                action.getStartDate(),
                action.getDueDate(),
                action.getReviewStatus() == ActionReviewStatus.PENDING,
                delayed,
                assigneeName,
                action.getProjectId(),
                projectTag,
                projectName,
                teamName,
                sourceMeetingTitle,
                action.getParentActionId(),
                parentActionTitle,
                childDoneCount,
                childTotalCount
        );
    }
}
