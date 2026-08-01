package com.module06.backend.reviewloop.judge;

/** AI의 추천 — 안건에 박히지 않고 별도로 "저는 X안을 추천해요 + 이유"를 담는다. 최종 선택은 사람. */
public record Recommendation(
        String pick,        // 추천 안건 letter
        double confidence,  // 0.0 ~ 1.0
        String reason
) {}
