package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ① 갭 검증 — 성공기준 하드게이트(acceptance) + findings source 태깅.
 * 성공기준 미충족은 점수와 무관하게 '미완성(INCOMPLETE)' (§02: 점수 95여도 성공기준 못 채우면 통과 못 함).
 */
class AcceptanceGateTest {

    private final JudgeScorer scorer = new JudgeScorer(
            Map.of("PERF_001", 15),
            Map.of(Severity.CRITICAL, 40, Severity.MINOR, 10),
            JudgeScorer.DEFAULT_PASS_THRESHOLD);

    private Finding judge(String rule, Severity sev) {
        return new Finding(rule, sev, "cat", "desc", "A.java", 10, Confidence.HIGH, FindingSource.JUDGE);
    }

    private Finding gate1(String rule) {
        return new Finding(rule, Severity.MINOR, "arch", "desc", "A.java", 5, Confidence.HIGH, FindingSource.GATE1);
    }

    private Finding acceptanceUnmet(String criterion) {
        return new Finding(criterion, Severity.MINOR, "acceptance", "성공기준 미충족", "A.java", 0,
                Confidence.HIGH, FindingSource.ACCEPTANCE);
    }

    @Test
    @DisplayName("성공기준 미충족이면 점수 무관 INCOMPLETE (완료 하드게이트)")
    void acceptanceUnmetForcesIncompleteRegardlessOfScore() {
        // acceptance 1건만 → 감점 없으니 score 100인데도 미완성
        JudgeVerdict v = scorer.score(List.of(acceptanceUnmet("AC_2")));
        assertThat(v.score()).isEqualTo(100);
        assertThat(v.decision()).isEqualTo(JudgeDecision.INCOMPLETE);
    }

    @Test
    @DisplayName("acceptance는 점수 가중치에서 제외된다 (품질 점수를 오염시키지 않음)")
    void acceptanceExcludedFromScore() {
        // PERF_001(15)만 감점 → 85. acceptance는 점수 계산에서 빠짐. 단 판정은 미충족이라 INCOMPLETE.
        JudgeVerdict v = scorer.score(List.of(judge("PERF_001", Severity.MINOR), acceptanceUnmet("AC_2")));
        assertThat(v.score()).isEqualTo(85);
        assertThat(v.decision()).isEqualTo(JudgeDecision.INCOMPLETE);
    }

    @Test
    @DisplayName("Gate1 + Judge findings를 한 스트림으로 합쳐 채점 (source 태깅)")
    void mergesGate1AndJudgeFindings() {
        // Gate1(ARCH, 기본 10) + Judge(PERF_001, 15) = 25 감점 → 75, acceptance 없음 → 품질 판정
        JudgeVerdict v = scorer.score(List.of(gate1("ARCH_001"), judge("PERF_001", Severity.MINOR)));
        assertThat(v.score()).isEqualTo(75);
        assertThat(v.decision()).isEqualTo(JudgeDecision.NEEDS_REVISION);
        assertThat(v.findings()).extracting(Finding::source)
                .containsExactlyInAnyOrder(FindingSource.GATE1, FindingSource.JUDGE);
    }

    @Test
    @DisplayName("성공기준 충족 + Critical이면 사람 승인 (acceptance 통과 후 severity 라우팅)")
    void criticalAfterAcceptanceMet() {
        JudgeVerdict v = scorer.score(List.of(judge("SEC_001", Severity.CRITICAL)));
        assertThat(v.decision()).isEqualTo(JudgeDecision.AWAITING_HUMAN);
    }
}
