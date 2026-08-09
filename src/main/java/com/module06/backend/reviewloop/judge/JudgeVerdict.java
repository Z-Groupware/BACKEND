package com.module06.backend.reviewloop.judge;

import java.util.List;

/** 결정론 채점 결과. 같은 findings면 항상 같은 verdict — 재현·감사 가능. */
public record JudgeVerdict(
        int score,
        boolean hasCritical,
        JudgeDecision decision,
        List<Finding> findings
) {}
