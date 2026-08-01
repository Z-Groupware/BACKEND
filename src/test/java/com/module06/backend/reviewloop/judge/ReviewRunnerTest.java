package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 반복 루프 + budget + 감사 로그 검증. RoundReviewer는 람다로 verdict를 직접 제어(결정론).
 * 실제 리뷰(ReviewLoop)·LLM은 여기 필요 없다 — 반복·중단·로깅 메커니즘만 본다.
 */
class ReviewRunnerTest {

    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);

    private JudgeVerdict verdict(JudgeDecision decision) {
        return new JudgeVerdict(decision == JudgeDecision.PASS ? 90 : 70,
                decision == JudgeDecision.AWAITING_HUMAN,
                decision, List.of());
    }

    private ReviewRunner runner(RoundReviewer reviewer, ReviewBudget budget, AuditLogWriter log) {
        return new ReviewRunner(reviewer, budget, log, clock, "claude-opus-4-8");
    }

    @Test
    @DisplayName("PASS면 1라운드에 종료, 로그 1줄")
    void passStopsImmediately() throws IOException {
        AuditLogWriter log = new AuditLogWriter(dir.resolve("pass.jsonl"));
        AuditSummary summary = runner((f, c) -> verdict(JudgeDecision.PASS), new ReviewBudget(), log)
                .run("A.java", "code");

        assertThat(summary.roundsUsed()).isEqualTo(1);
        assertThat(summary.terminatedByBudget()).isFalse();
        assertThat(Files.readAllLines(log.logFile())).hasSize(1);
    }

    @Test
    @DisplayName("AWAITING_HUMAN(Critical)도 즉시 종료")
    void criticalStopsImmediately() throws IOException {
        AuditLogWriter log = new AuditLogWriter(dir.resolve("human.jsonl"));
        AuditSummary summary = runner((f, c) -> verdict(JudgeDecision.AWAITING_HUMAN), new ReviewBudget(), log)
                .run("Pay.java", "code");

        assertThat(summary.roundsUsed()).isEqualTo(1);
        assertThat(summary.terminatedByBudget()).isFalse();
    }

    @Test
    @DisplayName("NEEDS_REVISION 2회 후 PASS → 3라운드에 정상 종료")
    void revisesThenPasses() throws IOException {
        AuditLogWriter log = new AuditLogWriter(dir.resolve("revise.jsonl"));
        AtomicInteger calls = new AtomicInteger();
        RoundReviewer reviewer = (f, c) ->
                verdict(calls.incrementAndGet() < 3 ? JudgeDecision.NEEDS_REVISION : JudgeDecision.PASS);

        AuditSummary summary = runner(reviewer, new ReviewBudget(), log).run("A.java", "code");

        assertThat(summary.roundsUsed()).isEqualTo(3);
        assertThat(summary.terminatedByBudget()).isFalse();
        assertThat(summary.finalVerdict().decision()).isEqualTo(JudgeDecision.PASS);
        assertThat(Files.readAllLines(log.logFile())).hasSize(3);
    }

    @Test
    @DisplayName("계속 NEEDS_REVISION → budget(6) 소진으로 종료, 마지막 기록 terminatedByBudget=true")
    void budgetExhaustionTerminates() throws IOException {
        AuditLogWriter log = new AuditLogWriter(dir.resolve("exhaust.jsonl"));
        AuditSummary summary = runner((f, c) -> verdict(JudgeDecision.NEEDS_REVISION), new ReviewBudget(), log)
                .run("A.java", "code");

        assertThat(summary.roundsUsed()).isEqualTo(6);
        assertThat(summary.terminatedByBudget()).isTrue();

        List<String> lines = Files.readAllLines(log.logFile());
        assertThat(lines).hasSize(6);
        assertThat(lines.get(5)).contains("\"terminatedByBudget\":true");  // 마지막 줄만 종료 표시
        assertThat(lines.get(0)).contains("\"terminatedByBudget\":false");
    }
}
