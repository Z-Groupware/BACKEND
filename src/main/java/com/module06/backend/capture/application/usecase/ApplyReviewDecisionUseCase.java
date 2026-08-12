package com.module06.backend.capture.application.usecase;

import java.time.LocalDate;

import com.module06.backend.capture.application.result.ReviewDecisionOutcome;
import com.module06.backend.capture.domain.model.RejectReason;
import com.module06.backend.capture.domain.model.ReviewDecision;

/*
 * RVW-02 · 액션 항목 수정·반려.
 *
 * **사람이 확정하는 이 순간이 시스템에서 유일한 쓰기 지점이다**(명세 RVW-02). action ·
 * review_log · meeting_tuple_vector 가 한 트랜잭션에 들어간다.
 */
public interface ApplyReviewDecisionUseCase {

    ReviewDecisionOutcome apply(ReviewDecisionCommand command);

    /*
     * 판정 하나.
     *
     * 2026-08-11 — title·detail 추가(제목·내용 인라인 수정), rejectReason 용도 변경.
     * 이제 rejectReason은 **REJECT 전용**이다(사람이 5종 중 직접 고름, RejectReason.
     * isHumanSelectable()=true인 값만 허용). MODIFY는 이 필드를 아예 안 받는다 — 담당자·
     * 기한·제목·내용 중 어떤 필드가 바뀌었는지는 assignee·dueDate·title·detail의 null
     * 여부로 판단하고, 그 개수만큼 review_log를 나눠 기록한다(ApplyReviewDecisionService).
     *
     * @param confirmedBy  판정한 사람. 라벨에 남는다 — 누가 정답이라고 했는지 모르면
     *                     라벨의 신뢰도를 나중에 되짚을 수 없다
     * @param rejectReason REJECT에는 필수다(422), 사람이 직접 고른 5종만 허용한다.
     *                     CONFIRM·MODIFY에 붙어 오면 거절한다
     * @param assignee     MODIFY 에서 바꾼 담당자. 안 바꿨으면 null
     * @param dueDate      MODIFY 에서 바꾼 기한. 안 바꿨으면 null
     * @param title        MODIFY 에서 바꾼 제목. 안 바꿨으면 null
     * @param detail       MODIFY 에서 바꾼 내용. 안 바꿨으면 null
     */
    record ReviewDecisionCommand(
            long companyId,
            long meetingId,
            long actionId,
            long confirmedBy,
            ReviewDecision decision,
            RejectReason rejectReason,
            Long assignee,
            LocalDate dueDate,
            String title,
            String detail,
            /*
             * 예정 시작일(#386 후속). 다른 다섯과 성질이 다르다 — **AI 산출물이 아니다.**
             * meeting_assignment_tuple 에 대응 컬럼이 없어 AI 가 애초에 내지 않고, 사람이
             * 이 화면에서 처음 정한다. 그래서 CONFIRM 에도 실릴 수 있고(고치는 것이 아니라
             * 정하는 것이다) review_log 에 WRONG_* 라벨을 만들지 않는다.
             */
            LocalDate plannedStartDate
    ) {
    }
}
