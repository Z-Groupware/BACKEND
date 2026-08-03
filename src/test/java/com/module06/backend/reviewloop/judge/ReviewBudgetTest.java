package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 전역 budget 카운터 — 6회에서 소진되어 무한 루프를 막는다. */
class ReviewBudgetTest {

    @Test
    @DisplayName("기본 max 6: 6회 소비하면 소진")
    void exhaustsAtMax() {
        ReviewBudget budget = new ReviewBudget();
        for (int i = 0; i < 6; i++) {
            assertThat(budget.isExhausted()).isFalse();
            budget.consume();
        }
        assertThat(budget.isExhausted()).isTrue();
        assertThat(budget.spent()).isEqualTo(6);
        assertThat(budget.remaining()).isZero();
    }

    @Test
    @DisplayName("remaining은 0 밑으로 안 내려간다")
    void remainingFloorsAtZero() {
        ReviewBudget budget = new ReviewBudget(2);
        budget.consume();
        budget.consume();
        budget.consume();  // 초과 소비
        assertThat(budget.remaining()).isZero();
        assertThat(budget.isExhausted()).isTrue();
    }
}
