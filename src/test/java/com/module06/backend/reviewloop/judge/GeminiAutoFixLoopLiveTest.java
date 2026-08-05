package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate 2 · 자동수정 루프 라이브(Gemini) — 찾기→고치기→재리뷰가 실제 LLM으로 도는지.
 * PERF_001 가중치를 25로 둬서 N+1 1건이면 75점(NEEDS_REVISION) → 자동수정 트리거.
 * 기대: Gemini가 N+1을 IN-배치로 고치고, 재리뷰에서 통과(또는 budget 내 수렴).
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@ExtendWith(SkipOnProviderUnavailable.class)
class GeminiAutoFixLoopLiveTest {

    @TempDir
    Path repoRoot;

    @Test
    @DisplayName("자동수정 루프: N+1 발견 → LLM이 수정 → 재리뷰")
    void autoFixLoopEndToEnd() throws IOException {
        String badCode = Files.readString(Path.of("review-loop/golden/perf001/QuizListN1.java.txt"));

        RoundReviewer reviewer = new ReviewLoop(
                new GeminiJudgeAdapter(),
                RuleCatalog.fromFile(Path.of("review-loop/rules.yaml")),
                new EvidenceValidator(repoRoot),
                new JudgeScorer(
                        Map.of("PERF_001", 25, "CONV_001", 25, "ARCH_003a", 25), // 25 → 1건이면 75 NEEDS_REVISION
                        Map.of(Severity.MINOR, 10, Severity.CRITICAL, 40),
                        JudgeScorer.DEFAULT_PASS_THRESHOLD));

        AutoFixRunner runner = new AutoFixRunner(
                reviewer,
                new GeminiCodeFixerAdapter(),
                new ReviewBudget(),
                new AuditLogWriter(repoRoot.resolve("autofix-log.jsonl")),
                Clock.systemDefaultZone(),
                "gemini",
                repoRoot);

        AutoFixResult result = runner.run("QuizListN1.java", badCode);

        // 데모 기록: 라운드·최종판정 + before/after 코드
        StringBuilder sb = new StringBuilder();
        sb.append("rounds=").append(result.roundsUsed())
          .append("  final=").append(result.finalVerdict().decision())
          .append("  score=").append(result.finalVerdict().score())
          .append("  terminatedByBudget=").append(result.terminatedByBudget()).append("\n\n");
        sb.append("===== BEFORE =====\n").append(badCode).append("\n\n");
        sb.append("===== AFTER (LLM 자동수정) =====\n").append(result.finalCode()).append('\n');
        Files.writeString(Path.of("build/gemini-autofix.txt"), sb.toString());

        assertThat(result.roundsUsed()).isGreaterThanOrEqualTo(2);   // 최소 1회 수정 시도
        assertThat(result.finalCode()).isNotEqualTo(badCode);        // 코드가 실제로 바뀜
    }
}
