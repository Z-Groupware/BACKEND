package com.module06.backend.reviewloop.judge;

/**
 * finding 출처 태그 — Gate 1(결정론)·Gate 2(Judge)·성공기준(acceptance)을 한 스트림에서 구분한다. (아티팩트 §01)
 * ACCEPTANCE = 성공기준 미충족 항목(완료 하드게이트용). score 가중치에서 제외된다(§02).
 */
public enum FindingSource {
    GATE1,
    /** LLM 판정 — 현재 실제로 생성되는 유일한 출처({@code GeminiJudgeAdapter}가 이걸로 만든다). */
    JUDGE,
    /**
     * 성공기준 미충족 → {@code JudgeDecision.INCOMPLETE}(차단).
     *
     * <p>⚠️ <b>미배선</b>: 이 값을 가진 finding을 만드는 프로덕션 경로가 없다(테스트에서만 쓴다).
     * {@link JudgeScorer}가 읽기는 하므로 코드는 완결돼 보이지만, 실제로는 {@code INCOMPLETE}에
     * 도달할 수 없다 — 훅·CI 문구의 "미완성이면 차단"은 현재 동작하지 않는다.
     * 배선하려면 성공기준 판정기가 이 소스로 finding을 넣어야 한다.
     * 배경·현황: review-loop/UNIFIED_DESIGN.md §8 · {@link RuleCatalog#blockingRules()}
     */
    ACCEPTANCE
}
