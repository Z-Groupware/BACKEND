package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pre-push 게이트 차단 정책 검증(key-free).
 * 차단 = 완료 하드게이트 미충족(INCOMPLETE) 또는 사람 승인 필요(AWAITING_HUMAN=Critical).
 * Minor(NEEDS_REVISION)와 통과(PASS)는 로컬 개발 흐름을 끊지 않도록 push를 막지 않는다.
 *
 * <p><b>매핑만 검사하면 안 된다</b> — 예전 이 테스트는 {@code isBlocking()}의 매핑만 봤다.
 * 매핑은 맞는데 <b>실제 카탈로그로는 두 차단 결정에 도달할 수 없는 상태</b>였고, 테스트는 초록이었다.
 * 그래서 아래에 실제 rules.yaml 기준 <b>도달 가능성</b>을 함께 고정한다.
 */
class PrePushGatePolicyTest {

    private static RuleCatalog realCatalog() throws IOException {
        return RuleCatalog.fromFile(Path.of("review-loop/rules.yaml"));
    }

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

    // ── 도달 가능성(현 상태 고정) ─────────────────────────────────────────────
    // 아래 두 테스트는 "현재 Gate 2는 차단할 수 없다"는 사실을 명시적으로 못박는다.
    // 누가 rules.yaml에 CRITICAL 규칙을 추가하면 이 테스트가 실패하며 갱신을 요구한다 →
    // 문서(훅·DRIVER.md·CI)가 약속하는 차단과 실제 동작이 조용히 갈리는 일이 다시 생기지 않는다.

    @Test
    @DisplayName("현재 rules.yaml에는 CRITICAL judge 규칙이 없다 → AWAITING_HUMAN 도달 불가")
    void awaitingHumanIsUnreachableWithCurrentCatalog() throws IOException {
        RuleCatalog catalog = realCatalog();

        assertThat(catalog.judgeRules()).hasSize(3);
        assertThat(catalog.blockingRules())
                .as("CRITICAL judge 규칙이 생기면 Gate 2가 차단 가능해진다 — 이 테스트와 훅 문구를 함께 갱신할 것")
                .isEmpty();

        // normalize가 severity를 카탈로그 값으로 덮으므로, LLM이 CRITICAL로 뱉어도 MINOR가 된다.
        Finding llmSaysCritical = new Finding("CONV_001", Severity.CRITICAL, "convention",
                "설명", "A.java", 1, Confidence.HIGH);
        List<Finding> normalized = catalog.normalize(List.of(llmSaysCritical));

        assertThat(normalized).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.MINOR);
    }

    @Test
    @DisplayName("acceptance 판정이 미배선이라 INCOMPLETE도 도달 불가 — 실제 카탈로그로 낼 수 있는 결정은 PASS/NEEDS_REVISION뿐")
    void onlyNonBlockingDecisionsAreReachable() throws IOException {
        RuleCatalog catalog = realCatalog();
        JudgeScorer scorer = new JudgeScorer(catalog.effectiveWeights(),
                catalog.scorePolicy().defaultWeightBySeverity(),
                catalog.scorePolicy().passThreshold());

        // judge 규칙 3개를 전부 최대치로 지적해도(감점 45) 차단이 아니라 NEEDS_REVISION이다.
        List<Finding> worst = catalog.judgeRules().stream()
                .map(r -> new Finding(r.id(), r.severity(), "c", "설명", "A.java", 1, Confidence.HIGH))
                .toList();
        JudgeVerdict verdict = scorer.score(catalog.normalize(worst));

        assertThat(verdict.hasCritical()).isFalse();
        assertThat(verdict.decision()).isEqualTo(JudgeDecision.NEEDS_REVISION);
        assertThat(ReviewLoopRunner.isBlocking(verdict.decision()))
                .as("Gate 2의 실효는 차단이 아니라 '수정 요청서 생성'이다(UNIFIED_DESIGN §8)")
                .isFalse();
    }
}
