package com.module06.backend.reviewloop.judge;

/** 루프 실행 요약 — 최종 verdict, 소비한 라운드 수, budget 소진으로 끊겼는지. */
public record AuditSummary(
        JudgeVerdict finalVerdict,
        int roundsUsed,
        boolean terminatedByBudget
) {}
