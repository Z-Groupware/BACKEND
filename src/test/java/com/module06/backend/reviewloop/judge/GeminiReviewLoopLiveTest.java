package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate 2 · 전체 루프 라이브(Gemini) — rules.yaml → 프롬프트 → LLM findings → Evidence(환각차단) → 결정론 점수 → verdict.
 * GEMINI_API_KEY 있을 때만 실행. provider만 Gemini일 뿐 나머지 루프는 프로덕션과 동일.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@ExtendWith(SkipOnProviderUnavailable.class)
class GeminiReviewLoopLiveTest {

    @TempDir
    Path repoRoot;

    @Test
    @DisplayName("전체 루프: 씨앗 N+1을 실제 LLM이 잡고 → 근거검증 → 결정론 점수 → verdict")
    void fullLoopEndToEnd() throws IOException {
        // 씨앗 N+1 코드를 모델이 지목할 실제 파일명으로 배치 → Evidence가 file:line을 검증할 수 있게
        String code = Files.readString(Path.of("review-loop/golden/perf001/QuizListN1.java.txt"));
        Files.writeString(repoRoot.resolve("QuizListN1.java"), code);

        ReviewLoop loop = new ReviewLoop(
                new GeminiJudgeAdapter(),                                   // provider = Gemini
                RuleCatalog.fromFile(Path.of("review-loop/rules.yaml")),    // 정책 = SSOT
                new EvidenceValidator(repoRoot),                            // 환각차단
                new JudgeScorer(                                            // 결정론 점수
                        Map.of("PERF_001", 15, "CONV_001", 15, "ARCH_003a", 15),
                        Map.of(Severity.MINOR, 10, Severity.CRITICAL, 40),
                        JudgeScorer.DEFAULT_PASS_THRESHOLD));

        JudgeVerdict verdict = loop.review("QuizListN1.java", code);

        // 데모: verdict 전체를 파일로 남긴다(확인용)
        StringBuilder sb = new StringBuilder();
        sb.append("score=").append(verdict.score())
          .append("  decision=").append(verdict.decision())
          .append("  hasCritical=").append(verdict.hasCritical())
          .append("  findings=").append(verdict.findings().size()).append('\n');
        verdict.findings().forEach(f -> sb.append("  - ").append(f).append('\n'));
        Files.writeString(Path.of("build/gemini-verdict.txt"), sb.toString());

        assertThat(verdict.findings())
                .as("근거 검증을 통과한 finding이 남아야 함")
                .isNotEmpty();
        assertThat(verdict.decision()).isNotNull();
    }
}
