package com.module06.backend.capture.presentation.api.response;

import com.module06.backend.capture.application.result.ReviewDecisionOutcome;

/*
 * RVW-02 응답이다.
 *
 * labelLogged 와 vectorQueued 를 따로 내려준다. 라벨은 남았는데 벡터 예약이 안 된 경우가
 * **정상적으로** 있다 — 반려 건(정답 tuple 이 없다) · 근거 발화가 없는 액션 · 수동 추가 건이다.
 * 하나로 합치면 화면이 "라벨이 안 남았다"로 읽고, 그건 다시 눌러야 하는 상태처럼 보인다.
 * 다시 눌러도 라벨 행만 하나 더 쌓인다.
 */
public record ReviewDecisionResponse(
        Long actionId,
        String reviewStatus,
        boolean labelLogged,
        boolean vectorQueued
) {

    public static ReviewDecisionResponse from(ReviewDecisionOutcome outcome) {
        return new ReviewDecisionResponse(
                outcome.actionId(),
                outcome.reviewStatus(),
                outcome.labelLogged(),
                outcome.vectorQueued());
    }
}
