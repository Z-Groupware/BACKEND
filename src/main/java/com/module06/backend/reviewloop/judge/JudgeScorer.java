package com.module06.backend.reviewloop.judge;

import java.util.List;
import java.util.Map;

/**
 * 점수는 LLM이 아니라 코드가 결정론으로 계산한다: score = 100 - Σ(weight). (rules.yaml SSOT)
 *
 * - CRITICAL finding이 하나라도 있으면 점수와 무관하게 사람 승인(AWAITING_HUMAN).
 * - 그 외엔 score >= passThreshold면 PASS, 아니면 NEEDS_REVISION.
 *
 * 이 클래스가 LLM 없이 순수 함수이기에 "AI가 점수를 지어냈다"는 반박이 성립하지 않는다 —
 * LLM은 findings만 뱉고, 점수와 라우팅은 재현·감사 가능한 이 로직이 결정한다.
 */
public class JudgeScorer {

    public static final int DEFAULT_PASS_THRESHOLD = 80;

    private final Map<String, Integer> weightByRuleId;        // 규칙별 weight 오버라이드
    private final Map<Severity, Integer> defaultWeightBySeverity;
    private final int passThreshold;

    public JudgeScorer(Map<String, Integer> weightByRuleId,
                       Map<Severity, Integer> defaultWeightBySeverity,
                       int passThreshold) {
        this.weightByRuleId = Map.copyOf(weightByRuleId);
        this.defaultWeightBySeverity = Map.copyOf(defaultWeightBySeverity);
        this.passThreshold = passThreshold;
    }

    /** 규칙별 오버라이드가 있으면 그 값을, 없으면 severity 기본 감점을 쓴다. */
    public int weightOf(Finding finding) {
        Integer perRule = weightByRuleId.get(finding.ruleId());
        if (perRule != null) {
            return perRule;
        }
        return defaultWeightBySeverity.getOrDefault(finding.severity(), 0);
    }

    public JudgeVerdict score(List<Finding> findings) {
        // 성공기준(acceptance)은 완료 판정 신호일 뿐 품질 점수에서 제외한다(§02). 미충족이 하나라도 있으면 완료 하드게이트에 걸린다.
        boolean acceptanceUnmet = findings.stream().anyMatch(f -> f.source() == FindingSource.ACCEPTANCE);

        int deduction = findings.stream()
                .filter(f -> f.source() != FindingSource.ACCEPTANCE)   // acceptance는 감점 제외
                .mapToInt(this::weightOf).sum();
        int score = Math.max(0, 100 - deduction);   // 0 미만으로 내려가지 않는다

        boolean hasCritical = findings.stream()
                .filter(f -> f.source() != FindingSource.ACCEPTANCE)
                .anyMatch(f -> f.severity() == Severity.CRITICAL);

        JudgeDecision decision;
        if (acceptanceUnmet) {
            decision = JudgeDecision.INCOMPLETE;               // 완료 하드게이트: 점수·severity 무관, 성공기준 못 채우면 미완성
        } else if (hasCritical) {
            decision = JudgeDecision.AWAITING_HUMAN;            // Critical은 점수 무관 사람 승인
        } else {
            decision = score >= passThreshold ? JudgeDecision.PASS : JudgeDecision.NEEDS_REVISION;
        }

        return new JudgeVerdict(score, hasCritical, decision, List.copyOf(findings));
    }
}
