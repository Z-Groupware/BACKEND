package com.module06.backend.capture.application.port.out;

/*
 * 회의 개요 계층 호출 결과.
 *
 * <h2>overview 가 비면 덮지 않는다</h2>
 * 모델이 빈 문자열이나 공백만 돌려주는 경우가 있다. 그걸 그대로 저장하면 개요 칸이 **비어 있는
 * 회의**가 만들어지는데, 그건 L3 가 이어 붙인 값보다 나쁘다. 호출자가 비어 있는지 보고 덮을지
 * 정한다 — 던지지 않는 이유는 개요 하나 때문에 회의를 실패시키지 않기 때문이다
 * (LayerName.OVERVIEW 주석).
 */
public record SummarizeMeetingResult(
        String overview,
        LayerRun run
) {

    /* 저장할 값이 있는가. 공백만 있는 응답은 없는 것으로 본다. */
    public boolean hasOverview() {
        return overview != null && !overview.isBlank();
    }
}
