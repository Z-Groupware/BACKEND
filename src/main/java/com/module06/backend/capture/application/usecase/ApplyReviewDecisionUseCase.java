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
     * @param confirmedBy  판정한 사람. 라벨에 남는다 — 누가 정답이라고 했는지 모르면
     *                     라벨의 신뢰도를 나중에 되짚을 수 없다
     * @param rejectReason MODIFY·REJECT 에는 필수다(422). CONFIRM 에 붙어 오면 거절한다 —
     *                     "맞혔는데 틀렸다"는 모순이고 DB CHECK 도 막는다
     * @param assignee     MODIFY 에서 바꾼 담당자. 안 바꿨으면 null
     * @param dueDate      MODIFY 에서 바꾼 기한. 안 바꿨으면 null
     */
    record ReviewDecisionCommand(
            long companyId,
            long meetingId,
            long actionId,
            long confirmedBy,
            ReviewDecision decision,
            RejectReason rejectReason,
            Long assignee,
            LocalDate dueDate
    ) {
    }
}
