package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채점 정책 SSOT 검증(key-free) — 가중치·임계값이 rules.yaml(meta.score)에서 오는지.
 * 목적은 드리프트 방지: 새 enforced_by:judge 규칙을 yaml에 추가했을 때
 * 코드(하드코딩 Map)를 같이 고치지 않아 조용히 다른 점수가 매겨지는 사고를 막는다.
 */
class RuleCatalogScoringTest {

    private static final String YAML = """
            meta:
              score:
                pass_threshold: 80
                default_weight:
                  CRITICAL: 40
                  MINOR: 10
                judge_default_weight: 15
            rules:
              - id: CONV_001
                text: "유틸 재발명 금지"
                severity: MINOR
                enforced_by: judge
              - id: NEW_JUDGE_1
                text: "나중에 추가된 의미규칙"
                severity: MINOR
                enforced_by: judge
              - id: PINNED_001
                text: "가중치를 명시한 규칙"
                severity: MINOR
                enforced_by: judge
                weight: 25
              - id: SEC_001
                text: "judge가 아닌 규칙(테스트가 집행)"
                severity: CRITICAL
                enforced_by: test
            """;

    @Test
    @DisplayName("meta.score를 읽는다 — 임계값·severity 기본감점·judge 기본감점")
    void parsesScorePolicy() {
        RuleCatalog.ScorePolicy policy = RuleCatalog.fromYaml(YAML).scorePolicy();

        assertThat(policy.passThreshold()).isEqualTo(80);
        assertThat(policy.judgeDefaultWeight()).isEqualTo(15);
        assertThat(policy.defaultWeightBySeverity())
                .containsEntry(Severity.CRITICAL, 40)
                .containsEntry(Severity.MINOR, 10);
    }

    @Test
    @DisplayName("새 judge 규칙은 코드 수정 없이 judge_default_weight를 받는다 (드리프트 방지)")
    void newJudgeRuleGetsDefaultWeightWithoutCodeChange() {
        assertThat(RuleCatalog.fromYaml(YAML).effectiveWeights())
                .containsEntry("CONV_001", 15)
                .containsEntry("NEW_JUDGE_1", 15);   // 하드코딩 Map이었다면 여기서 누락됐을 규칙
    }

    @Test
    @DisplayName("규칙에 weight가 명시되면 judge_default_weight보다 우선한다")
    void explicitWeightWins() {
        assertThat(RuleCatalog.fromYaml(YAML).effectiveWeights()).containsEntry("PINNED_001", 25);
    }

    @Test
    @DisplayName("judge가 아닌 규칙은 채점 대상이 아니다 (normalize 화이트리스트와 일관)")
    void nonJudgeRuleIsNotWeighted() {
        assertThat(RuleCatalog.fromYaml(YAML).effectiveWeights()).doesNotContainKey("SEC_001");
    }

    @Test
    @DisplayName("meta.score가 없으면 문서화된 기본값으로 떨어진다")
    void fallsBackToDefaultWhenScoreSectionMissing() {
        RuleCatalog.ScorePolicy policy = RuleCatalog.fromYaml("""
                rules:
                  - id: CONV_001
                    text: "유틸 재발명 금지"
                    severity: MINOR
                    enforced_by: judge
                """).scorePolicy();

        assertThat(policy).isEqualTo(RuleCatalog.ScorePolicy.DEFAULT);
    }

    @Test
    @DisplayName("forDomain으로 걸러도 채점 정책은 보존된다")
    void scorePolicySurvivesDomainFilter() {
        RuleCatalog filtered = RuleCatalog.fromYaml(YAML).forDomain("payment");

        assertThat(filtered.scorePolicy().judgeDefaultWeight()).isEqualTo(15);
    }

    @Test
    @DisplayName("회귀 방지 — 실제 rules.yaml의 judge 규칙 3개가 종전과 같은 15점을 유지한다")
    void realCatalogKeepsExistingWeights() throws Exception {
        RuleCatalog catalog = RuleCatalog.fromFile(Path.of("review-loop/rules.yaml"));

        assertThat(catalog.scorePolicy().passThreshold()).isEqualTo(JudgeScorer.DEFAULT_PASS_THRESHOLD);
        assertThat(catalog.effectiveWeights())
                .containsEntry("CONV_001", 15)
                .containsEntry("PERF_001", 15)
                .containsEntry("ARCH_003a", 15);
    }
}
