package com.module06.backend.reviewloop.judge;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 반복 위반 패턴 자동 감지(아티팩트 "정책 자기진화"의 씨앗) — 같은 규칙이 반복해서 걸리면
 * "정책이 불명확할 수 있다"는 신호로 보고 사람에게 제안한다(사람이 카탈로그 손볼지 결정).
 * 결정론(단순 카운트) — LLM 불필요.
 */
public class RepeatedPatternDetector {

    /** ruleId가 threshold 이상 반복됨 → 정책 리뷰 제안. */
    public record Alert(String ruleId, long count) {}

    private final int threshold;

    public RepeatedPatternDetector(int threshold) {
        this.threshold = threshold;
    }

    public List<Alert> detect(List<Finding> history) {
        Map<String, Long> counts = history.stream()
                .collect(Collectors.groupingBy(Finding::ruleId, Collectors.counting()));
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(e -> new Alert(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();
    }
}
