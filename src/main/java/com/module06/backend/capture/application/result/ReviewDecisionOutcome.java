package com.module06.backend.capture.application.result;

/*
 * RVW-02 판정 결과다.
 *
 * <h2>labelLogged 와 vectorQueued 를 따로 내려주는 이유</h2>
 * 라벨은 남았는데 벡터 예약이 안 된 경우가 정상적으로 있다 — 근거 발화가 없는 액션(수동 추가)
 * 이나 반려 건이다. 하나로 합치면 화면이 "라벨이 안 남았다"로 읽고, 그건 다시 판정해야 하는
 * 상태처럼 보인다. 실제로 다시 눌러도 라벨 행만 하나 더 쌓인다.
 */
public record ReviewDecisionOutcome(
        long actionId,
        String reviewStatus,
        boolean labelLogged,
        boolean vectorQueued
) {
}
