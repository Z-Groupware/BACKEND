package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate 2 · 결정론 채점 검증 — 점수는 코드가 낸다(LLM 아님). rules.yaml 가중치 기반.
 */
class JudgeScorerTest {

    private final JudgeScorer scorer = new JudgeScorer(
            Map.of("PERF_001", 15, "CONV_001", 15),                 // judge 규칙 오버라이드
            Map.of(Severity.CRITICAL, 40, Severity.MINOR, 10),      // severity 기본 감점
            JudgeScorer.DEFAULT_PASS_THRESHOLD);                    // 80

    private Finding minor(String ruleId) {
        return new Finding(ruleId, Severity.MINOR, "arch", "설명", "A.java", 10, Confidence.HIGH);
    }

    private Finding critical(String ruleId) {
        return new Finding(ruleId, Severity.CRITICAL, "security", "설명", "B.java", 20, Confidence.HIGH);
    }

    @Test
    @DisplayName("위반 없음 → 100점 PASS")
    void noFindingsIsPerfectPass() {
        JudgeVerdict v = scorer.score(List.of());
        assertThat(v.score()).isEqualTo(100);
        assertThat(v.decision()).isEqualTo(JudgeDecision.PASS);
        assertThat(v.hasCritical()).isFalse();
    }

    @Test
    @DisplayName("MINOR 1건(10) → 90점 PASS")
    void singleMinorStillPasses() {
        assertThat(scorer.score(List.of(minor("ARCH_001"))).score()).isEqualTo(90);
        assertThat(scorer.score(List.of(minor("ARCH_001"))).decision()).isEqualTo(JudgeDecision.PASS);
    }

    @Test
    @DisplayName("판정 규칙 오버라이드: PERF_001은 15점 감점")
    void perRuleWeightOverridesSeverityDefault() {
        // PERF_001(15) + ARCH_001(기본 10) = 25 감점 → 75
        JudgeVerdict v = scorer.score(List.of(minor("PERF_001"), minor("ARCH_001")));
        assertThat(v.score()).isEqualTo(75);
        assertThat(v.decision()).isEqualTo(JudgeDecision.NEEDS_REVISION);  // 80 미만
    }

    @Test
    @DisplayName("점수 80 미만이면 NEEDS_REVISION (자동수정 재시도 대상)")
    void belowThresholdNeedsRevision() {
        // MINOR 3건 = 30 감점 → 70
        JudgeVerdict v = scorer.score(List.of(minor("ARCH_001"), minor("ARCH_002"), minor("ARCH_003")));
        assertThat(v.score()).isEqualTo(70);
        assertThat(v.decision()).isEqualTo(JudgeDecision.NEEDS_REVISION);
    }

    @Test
    @DisplayName("CRITICAL은 점수와 무관하게 AWAITING_HUMAN (결제·보안 자동화 금지)")
    void criticalAlwaysGoesToHuman() {
        // CRITICAL 1건만: 40 감점 → 60점이지만, 점수 무관 사람 승인
        JudgeVerdict v = scorer.score(List.of(critical("SEC_001")));
        assertThat(v.hasCritical()).isTrue();
        assertThat(v.decision()).isEqualTo(JudgeDecision.AWAITING_HUMAN);
    }

    @Test
    @DisplayName("점수는 0 미만으로 내려가지 않는다 (floor 0)")
    void scoreIsFlooredAtZero() {
        List<Finding> many = List.of(
                minor("ARCH_001"), minor("ARCH_002"), minor("ARCH_003"),
                minor("ARCH_004"), minor("ARCH_005"), minor("ARCH_006"),
                minor("ARCH_007"), minor("ARCH_008"), minor("ARCH_009"),
                minor("ARCH_010"), minor("ARCH_011"));  // 11 × 10 = 110 감점
        assertThat(scorer.score(many).score()).isZero();
    }

    @Test
    @DisplayName("결정론: 같은 findings면 항상 같은 점수 (재현·감사 가능)")
    void deterministicForSameInput() {
        List<Finding> f = List.of(minor("ARCH_001"), minor("PERF_001"));
        assertThat(scorer.score(f).score()).isEqualTo(scorer.score(f).score());
    }
}
