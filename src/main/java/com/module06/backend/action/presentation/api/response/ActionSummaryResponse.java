package com.module06.backend.action.presentation.api.response;

import java.time.LocalDate;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;

/* comment.
    액션 목록/카드용 요약 응답 DTO. 개인 액션 목록·팀 액션 목록·타임라인·회의별 조회에서
    공용으로 쓰인다 — actionType(TEAM/PERSONAL) 필드로 화면이 구분해서 렌더링한다.
    담을 값: id·actionType·title·status·dueDate·needsReview·지연 배지(파생값).
    needsReview는 reviewStatus == PENDING 여부다(AI 분배 직후만 참). isDelayed는 저장값이
    아니라 dueDate 기준으로 조회 시 계산된다.

    연결된 클래스
    - ActionController · TeamActionController : 이 DTO를 내보내는 진입점
    - ActionService · TeamActionService        : 이 DTO를 만드는 구현체
*/
public record ActionSummaryResponse(
        Long id,
        ActionType actionType,
        String title,
        ActionStatus status,
        LocalDate dueDate,
        boolean needsReview,
        boolean isDelayed
) {

    public static ActionSummaryResponse from(Action action) {
        LocalDate today = LocalDate.now();
        boolean delayed = action.getStatus() != ActionStatus.DONE
                && action.getDueDate() != null && action.getDueDate().isBefore(today);

        return new ActionSummaryResponse(
                action.getId(),
                action.getActionType(),
                action.getTitle(),
                action.getStatus(),
                action.getDueDate(),
                action.getReviewStatus() == ActionReviewStatus.PENDING,
                delayed
        );
    }
}
