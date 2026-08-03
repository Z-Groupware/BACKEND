package com.module06.backend.reviewloop.judge;

/**
 * 감사 로그 한 줄(error_log.jsonl) — "언제/어떤 모델로/몇 점/무슨 판정"을 재현 가능하게 남긴다. (아티팩트 §01 ③)
 * 결제 도메인이면 이건 선택이 아니라 컴플라이언스에 가까운 추적성이다.
 */
public record AuditRecord(
        String timestamp,
        int round,
        String model,
        int score,
        boolean hasCritical,
        JudgeDecision decision,
        int findingsCount,
        boolean terminatedByBudget
) {}
