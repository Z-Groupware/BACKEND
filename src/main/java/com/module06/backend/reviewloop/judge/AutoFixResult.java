package com.module06.backend.reviewloop.judge;

/** 자동수정 루프 결과 — 최종 코드/판정, 소비 라운드, budget 소진 여부. */
public record AutoFixResult(
        String finalCode,
        JudgeVerdict finalVerdict,
        int roundsUsed,
        boolean terminatedByBudget
) {}
