package com.module06.backend.reviewloop.judge;

/**
 * finding 출처 태그 — Gate 1(결정론)·Gate 2(Judge)·성공기준(acceptance)을 한 스트림에서 구분한다. (아티팩트 §01)
 * ACCEPTANCE = 성공기준 미충족 항목(완료 하드게이트용). score 가중치에서 제외된다(§02).
 */
public enum FindingSource {
    GATE1,
    JUDGE,
    ACCEPTANCE
}
