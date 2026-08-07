package com.module06.backend.capture.application.result;

/*
 * RVW-03 결과다.
 *
 * isManual 과 reviewStatus 를 함께 돌려주는 이유 — 화면이 방금 넣은 항목을 **AI 가 뽑은 것과
 * 다르게** 그려야 한다. 수동 추가 건은 검토 대상이 아니라 이미 확정된 것이고(사람이 직접 넣었다),
 * 게이트 신호도 근거 발화도 없다.
 */
public record ReviewActionAdded(
        long actionId,
        boolean isManual,
        String reviewStatus
) {
}
