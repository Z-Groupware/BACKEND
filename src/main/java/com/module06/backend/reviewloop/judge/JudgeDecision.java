package com.module06.backend.reviewloop.judge;

/**
 * 채점 후 라우팅 결정.
 * PASS           : 통과
 * NEEDS_REVISION : 점수 미달 → 자동수정 재시도(≤3회, budget 합산)
 * AWAITING_HUMAN : Critical 포함 → 점수와 무관하게 사람 승인
 */
public enum JudgeDecision {
    PASS,
    NEEDS_REVISION,   // 품질(score) 미달 → 다시 수정
    AWAITING_HUMAN,   // Critical → 사람 승인
    INCOMPLETE        // 완료 하드게이트: 성공기준(acceptance) 미충족 → 기능 미완성, 재구현
}
