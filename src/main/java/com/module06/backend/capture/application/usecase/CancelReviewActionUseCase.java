package com.module06.backend.capture.application.usecase;

/*
 * RVW-04 · 직접 추가한 액션 취소.
 *
 * **지울 수 있는 것은 사람이 직접 넣은 액션뿐이다.** AI 가 만든 액션은 지우는 것이 아니라
 * 반려(RVW-02 REJECT)한다 — 지우면 「AI 가 이런 걸 뽑았고 사람이 아니라고 했다」는 쌍이
 * 사라지고, 그게 정확도 개선의 재료다. 지나간 회의는 다시 만들 수 없어 복구도 안 된다.
 */
public interface CancelReviewActionUseCase {

    void cancel(CancelReviewActionCommand command);

    record CancelReviewActionCommand(
            long companyId,
            long meetingId,
            long actionId,
            long requestedBy
    ) {
    }
}
