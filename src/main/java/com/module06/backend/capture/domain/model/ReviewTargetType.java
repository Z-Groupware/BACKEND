package com.module06.backend.capture.domain.model;

/*
 * 라벨이 무엇에 붙었는지다(review_log.target_type · V5.9).
 *
 * 한 테이블에 둘을 담는 이유 — 라벨셋은 "어느 계층이 얼마나 틀렸나"로 집계되고, 그 질문은
 * 액션과 요약 항목에 걸쳐 있다. 표를 나누면 계층별 집계가 두 표를 합쳐야 나온다.
 */
public enum ReviewTargetType {

    /* action.id — RVW-02 가 남긴다. */
    ACTION,

    /* meeting_decision.id — ANLZ-04(요약 항목 수정)가 남긴다. */
    SUMMARY_ITEM
}
