package com.module06.backend.project.presentation.api.response;

import java.time.LocalDate;

import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.project.application.usecase.GetProjectTimelineUseCase.TimelineItem;

/* comment.
    프로젝트 타임라인 탭의 한 행 = 팀 액션 카드 응답 DTO.
    담을 값: actionId·팀 액션 제목·부서명(teamName)·상태·마감일·지연 여부(isDelayed).
    actionId가 필수다 — 한 팀이 같은 프로젝트에 팀 액션을 여러 개 가질 수 있어서
    (부서 기준으로는 행을 특정할 수 없다). FE는 이 id로 팀 액션 상세로 내려간다.
    isDelayed는 마감일 기준 파생값이며 DB status(3값)와 별개의 배지다 — GetProjectTimelineUseCase가 계산해 넘긴다.

    연결된 클래스
    - ProjectController         : 이 DTO를 내보내는 진입점
    - ProjectService            : 이 DTO를 만드는 구현체
    - action 도메인             : actionId가 가리키는 팀 액션의 소유 도메인
*/
public record ProjectTimelineItemResponse(
        Long actionId,
        String title,
        Long teamId,
        String teamName,
        ActionStatus status,
        LocalDate dueDate,
        boolean isDelayed
) {

    public static ProjectTimelineItemResponse from(TimelineItem item) {
        return new ProjectTimelineItemResponse(
                item.actionId(),
                item.title(),
                item.teamId(),
                item.teamName(),
                item.status(),
                item.dueDate(),
                item.isDelayed()
        );
    }
}
