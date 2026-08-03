package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate 2 · 파이프라인 E2E 배선 검증 — findings → Evidence(환각 제거) → Scorer(결정론 점수) → 라우팅.
 *
 * LlmJudgePort는 stub으로 대체한다(테스트는 결정론이어야 하므로 실제 LLM을 넣지 않는다).
 * 실제 LLM 호출은 ClaudeJudgeAdapter(같은 포트 구현체)가 런타임·Promptfoo에서 담당한다.
 */
class JudgePipelineTest {

    @TempDir
    Path repoRoot;

    private JudgeScorer scorer() {
        return new JudgeScorer(
                Map.of("PERF_001", 15, "CONV_001", 15),
                Map.of(Severity.CRITICAL, 40, Severity.MINOR, 10),
                JudgeScorer.DEFAULT_PASS_THRESHOLD);
    }

    @Test
    @DisplayName("환각 finding은 Evidence에서 제거되어 점수·라우팅에 영향을 주지 않는다")
    void hallucinationIsDroppedBeforeScoring() throws IOException {
        Files.writeString(repoRoot.resolve("Svc.java"), "x\n".repeat(50));

        // stub: 근거 있는 PERF_001 1건 + 없는 파일을 가리키는 환각 1건
        LlmJudgePort stub = (file, code, policy) -> List.of(
                new Finding("PERF_001", Severity.MINOR, "perf", "N+1 의심", "Svc.java", 12, Confidence.HIGH),
                new Finding("CONV_001", Severity.MINOR, "conv", "유틸 재발명", "Ghost.java", 3, Confidence.MEDIUM));

        List<Finding> raw = stub.review("Svc.java", "...", "policy");
        List<Finding> grounded = new EvidenceValidator(repoRoot).keepGrounded(raw);
        JudgeVerdict verdict = scorer().score(grounded);

        assertThat(grounded).hasSize(1);                      // 환각 제거됨
        assertThat(verdict.score()).isEqualTo(85);            // PERF_001(15)만 감점 → 85
        assertThat(verdict.decision()).isEqualTo(JudgeDecision.PASS);
    }

    @Test
    @DisplayName("씨앗 위반 다수(근거 O) → 점수 미달 → NEEDS_REVISION (자동수정 재시도)")
    void multipleGroundedFindingsNeedRevision() throws IOException {
        Files.writeString(repoRoot.resolve("A.java"), "x\n".repeat(50));

        LlmJudgePort stub = (file, code, policy) -> List.of(
                new Finding("PERF_001", Severity.MINOR, "perf", "N+1", "A.java", 12, Confidence.HIGH),
                new Finding("CONV_001", Severity.MINOR, "conv", "재발명", "A.java", 20, Confidence.HIGH));

        List<Finding> grounded = new EvidenceValidator(repoRoot).keepGrounded(stub.review("A.java", "..", ".."));
        JudgeVerdict verdict = scorer().score(grounded);

        assertThat(verdict.score()).isEqualTo(70);            // 15+15 = 30 감점
        assertThat(verdict.decision()).isEqualTo(JudgeDecision.NEEDS_REVISION);
    }

    @Test
    @DisplayName("Critical 씨앗(근거 O) → 점수와 무관하게 AWAITING_HUMAN")
    void criticalRoutesToHuman() throws IOException {
        Files.writeString(repoRoot.resolve("Pay.java"), "x\n".repeat(50));

        LlmJudgePort stub = (file, code, policy) -> List.of(
                new Finding("SEC_001", Severity.CRITICAL, "security", "인증 만료값 변경", "Pay.java", 5, Confidence.HIGH));

        List<Finding> grounded = new EvidenceValidator(repoRoot).keepGrounded(stub.review("Pay.java", "..", ".."));
        JudgeVerdict verdict = scorer().score(grounded);

        assertThat(verdict.hasCritical()).isTrue();
        assertThat(verdict.decision()).isEqualTo(JudgeDecision.AWAITING_HUMAN);
    }
}
