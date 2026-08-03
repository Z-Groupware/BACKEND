package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate 2 · 루프 배선 E2E — rules.yaml 정책이 실제 호출까지 흐르고, 환각 제거 후 결정론 채점되는지.
 * LlmJudgePort는 stub(결정론 테스트). 라이브는 ClaudeJudgeAdapter가 같은 자리에 들어간다.
 */
class ReviewLoopTest {

    private static final String YAML = """
            rules:
              - id: ARCH_001
                text: "컨트롤러 로직 금지"
                severity: MINOR
                enforced_by: archunit
              - id: CONV_001
                text: "유틸 재발명 금지"
                severity: MINOR
                enforced_by: judge
              - id: PERF_001
                text: "N+1 금지"
                severity: MINOR
                enforced_by: judge
            """;

    @TempDir
    Path repoRoot;

    @Test
    @DisplayName("rules.yaml 정책 → 호출 전달, 환각 제거, 결정론 채점까지 한 번에 배선된다")
    void wiresPolicyThroughToScoredVerdict() throws IOException {
        Files.writeString(repoRoot.resolve("Svc.java"), "x\n".repeat(30));

        AtomicReference<String> deliveredPolicy = new AtomicReference<>();
        LlmJudgePort stub = (file, code, policy) -> {
            deliveredPolicy.set(policy);   // 실제로 어떤 정책이 전달됐는지 포착
            return List.of(
                    new Finding("PERF_001", Severity.MINOR, "perf", "N+1", "Svc.java", 5, Confidence.HIGH),
                    new Finding("CONV_001", Severity.MINOR, "conv", "재발명", "Ghost.java", 3, Confidence.MEDIUM));
        };

        ReviewLoop loop = new ReviewLoop(
                stub,
                RuleCatalog.fromYaml(YAML),
                new EvidenceValidator(repoRoot),
                new JudgeScorer(Map.of("PERF_001", 15, "CONV_001", 15),
                        Map.of(Severity.MINOR, 10, Severity.CRITICAL, 40),
                        JudgeScorer.DEFAULT_PASS_THRESHOLD));

        JudgeVerdict verdict = loop.review("Svc.java", "the code");

        // 1) rules.yaml에서 뽑은 judge 규칙이 실제 LLM 호출 정책으로 전달됨 (SSOT 연결)
        assertThat(deliveredPolicy.get()).contains("CONV_001", "PERF_001");
        assertThat(deliveredPolicy.get()).doesNotContain("ARCH_001");  // 결정론 규칙은 Judge 정책에서 제외

        // 2) 환각(Ghost.java) 제거 후, 근거 있는 PERF_001만 채점 → 85 PASS
        assertThat(verdict.findings()).hasSize(1);
        assertThat(verdict.score()).isEqualTo(85);
        assertThat(verdict.decision()).isEqualTo(JudgeDecision.PASS);
    }

    @Test
    @DisplayName("severity는 LLM 변덕이 아니라 카탈로그 값으로 정규화되고, 카탈로그에 없는 ruleId는 제거된다")
    void normalizesSeverityAndDropsUnknownRuleId() throws IOException {
        Files.writeString(repoRoot.resolve("Svc.java"), "x\n".repeat(30));

        LlmJudgePort stub = (file, code, policy) -> List.of(
                // LLM이 MINOR 규칙(CONV_001)에 CRITICAL을 잘못 매김 → 카탈로그(MINOR)로 교정돼야 함
                new Finding("CONV_001", Severity.CRITICAL, "conv", "재발명", "Svc.java", 5, Confidence.HIGH),
                // 카탈로그에 없는 규칙(환각) → 제거돼야 함
                new Finding("GHOST_999", Severity.CRITICAL, "x", "지어냄", "Svc.java", 6, Confidence.LOW));

        ReviewLoop loop = new ReviewLoop(
                stub,
                RuleCatalog.fromYaml(YAML),
                new EvidenceValidator(repoRoot),
                new JudgeScorer(Map.of("CONV_001", 15),
                        Map.of(Severity.MINOR, 10, Severity.CRITICAL, 40),
                        JudgeScorer.DEFAULT_PASS_THRESHOLD));

        JudgeVerdict verdict = loop.review("Svc.java", "the code");

        // 환각 ruleId(GHOST_999) 제거 → CONV_001 하나만 남는다
        assertThat(verdict.findings()).hasSize(1);
        assertThat(verdict.findings().get(0).ruleId()).isEqualTo("CONV_001");
        // severity가 카탈로그(MINOR)로 교정 → CRITICAL 오라우팅이 닫힌다
        assertThat(verdict.findings().get(0).severity()).isEqualTo(Severity.MINOR);
        assertThat(verdict.hasCritical()).isFalse();
        // weight 15 감점 → 85 PASS (정규화 없었다면 AWAITING_HUMAN으로 잘못 갔을 것)
        assertThat(verdict.score()).isEqualTo(85);
        assertThat(verdict.decision()).isEqualTo(JudgeDecision.PASS);
    }
}
