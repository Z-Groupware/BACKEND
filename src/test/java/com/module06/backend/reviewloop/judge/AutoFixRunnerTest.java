package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자동수정 루프 검증 — reviewer/fixer는 stub(결정론). 반복·종료·수정 반영·budget을 본다. (LLM 없음)
 */
class AutoFixRunnerTest {

    @TempDir
    Path repoRoot;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);

    private JudgeVerdict verdict(JudgeDecision d) {
        return new JudgeVerdict(d == JudgeDecision.PASS ? 100 : 70, d == JudgeDecision.AWAITING_HUMAN, d, List.of());
    }

    private AutoFixRunner runner(RoundReviewer reviewer, CodeFixerPort fixer, ReviewBudget budget) {
        return new AutoFixRunner(reviewer, fixer, budget,
                new AuditLogWriter(repoRoot.resolve("log.jsonl")), clock, "stub-model", repoRoot);
    }

    @Test
    @DisplayName("NEEDS_REVISION → 고침 → PASS: 2라운드에 수정 완료, 최종코드는 고쳐진 것")
    void fixesThenPasses() throws IOException {
        AtomicInteger reviews = new AtomicInteger();
        RoundReviewer reviewer = (f, c) -> verdict(reviews.incrementAndGet() == 1
                ? JudgeDecision.NEEDS_REVISION : JudgeDecision.PASS);
        CodeFixerPort fixer = (f, c, findings) -> "FIXED CODE";

        AutoFixResult result = runner(reviewer, fixer, new ReviewBudget()).run("A.java", "BAD CODE");

        assertThat(result.roundsUsed()).isEqualTo(2);
        assertThat(result.terminatedByBudget()).isFalse();
        assertThat(result.finalVerdict().decision()).isEqualTo(JudgeDecision.PASS);
        assertThat(result.finalCode()).isEqualTo("FIXED CODE");   // 수정이 반영됨
    }

    @Test
    @DisplayName("Critical(AWAITING_HUMAN)이면 자동수정 안 하고 즉시 사람에게")
    void criticalStopsWithoutFixing() throws IOException {
        AtomicInteger fixCalls = new AtomicInteger();
        CodeFixerPort fixer = (f, c, findings) -> { fixCalls.incrementAndGet(); return "SHOULD NOT HAPPEN"; };

        AutoFixResult result = runner((f, c) -> verdict(JudgeDecision.AWAITING_HUMAN), fixer, new ReviewBudget())
                .run("Pay.java", "CODE");

        assertThat(result.roundsUsed()).isEqualTo(1);
        assertThat(result.finalVerdict().decision()).isEqualTo(JudgeDecision.AWAITING_HUMAN);
        assertThat(fixCalls.get()).isZero();   // fixer 호출 안 됨
    }

    @Test
    @DisplayName("계속 고쳐도 NEEDS_REVISION이면 budget(6)에서 종료 → 사람 이관")
    void neverConvergesTerminatesByBudget() throws IOException {
        AutoFixResult result = runner(
                (f, c) -> verdict(JudgeDecision.NEEDS_REVISION),
                (f, c, findings) -> "still bad",
                new ReviewBudget()).run("A.java", "CODE");

        assertThat(result.roundsUsed()).isEqualTo(6);
        assertThat(result.terminatedByBudget()).isTrue();
    }
}
