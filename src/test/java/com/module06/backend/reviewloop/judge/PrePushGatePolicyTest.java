package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pre-push 게이트 차단 정책 검증(key-free).
 * 차단 = 완료 하드게이트 미충족(INCOMPLETE) 또는 사람 승인 필요(AWAITING_HUMAN=Critical).
 * Minor(NEEDS_REVISION)와 통과(PASS)는 로컬 개발 흐름을 끊지 않도록 push를 막지 않는다.
 */
class PrePushGatePolicyTest {

    @Test
    @DisplayName("INCOMPLETE·AWAITING_HUMAN은 push 차단")
    void blocksIncompleteAndAwaitingHuman() {
        assertThat(ReviewLoopRunner.isBlocking(JudgeDecision.INCOMPLETE)).isTrue();
        assertThat(ReviewLoopRunner.isBlocking(JudgeDecision.AWAITING_HUMAN)).isTrue();
    }

    @Test
    @DisplayName("PASS·NEEDS_REVISION(Minor·score<80)은 push 통과")
    void passesPassAndNeedsRevision() {
        assertThat(ReviewLoopRunner.isBlocking(JudgeDecision.PASS)).isFalse();
        assertThat(ReviewLoopRunner.isBlocking(JudgeDecision.NEEDS_REVISION)).isFalse();
    }
}
