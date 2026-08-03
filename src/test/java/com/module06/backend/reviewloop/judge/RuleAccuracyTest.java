package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 규칙 정확도 집계 — 오탐률 = 오탐 ÷ (오탐+CONFIRMED), MISSED는 분모에서 제외. */
class RuleAccuracyTest {

    private Lesson lesson(String rule, LessonKind kind) {
        return new Lesson("2026-07-18T00:00:00", rule, kind, "note");
    }

    private RuleAccuracy.Stat stat(List<RuleAccuracy.Stat> stats, String rule) {
        return stats.stream().filter(s -> s.ruleId().equals(rule)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("오탐률 = 오탐 ÷ (오탐+CONFIRMED) — CONFIRMED가 분모를 채운다")
    void computesFalsePositiveRateFromReviewedDecisions() {
        // CONV_001: 오탐 3, 확정 1 → 3/4 = 75%
        List<RuleAccuracy.Stat> stats = RuleAccuracy.summarize(List.of(
                lesson("CONV_001", LessonKind.FALSE_POSITIVE),
                lesson("CONV_001", LessonKind.FALSE_POSITIVE),
                lesson("CONV_001", LessonKind.FALSE_POSITIVE),
                lesson("CONV_001", LessonKind.CONFIRMED)));

        RuleAccuracy.Stat conv = stat(stats, "CONV_001");
        assertThat(conv.falsePositives()).isEqualTo(3);
        assertThat(conv.confirmed()).isEqualTo(1);
        assertThat(conv.reviewed()).isEqualTo(4);
        assertThat(conv.falsePositiveRate()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    @DisplayName("MISSED는 오탐률 분모에서 제외된다(recall 지표라 별개)")
    void missedDoesNotAffectRate() {
        // 오탐 1, 확정 1, 놓침 5 → 오탐률 = 1/2 = 50% (MISSED 무관)
        List<RuleAccuracy.Stat> stats = RuleAccuracy.summarize(List.of(
                lesson("PERF_001", LessonKind.FALSE_POSITIVE),
                lesson("PERF_001", LessonKind.CONFIRMED),
                lesson("PERF_001", LessonKind.MISSED),
                lesson("PERF_001", LessonKind.MISSED),
                lesson("PERF_001", LessonKind.MISSED),
                lesson("PERF_001", LessonKind.MISSED),
                lesson("PERF_001", LessonKind.MISSED)));

        RuleAccuracy.Stat perf = stat(stats, "PERF_001");
        assertThat(perf.missed()).isEqualTo(5);
        assertThat(perf.reviewed()).isEqualTo(2);
        assertThat(perf.falsePositiveRate()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("경계값: 전부 오탐이면 오탐률 100%")
    void rateIsOneWhenAllFalsePositive() {
        List<RuleAccuracy.Stat> stats = RuleAccuracy.summarize(List.of(
                lesson("CONV_001", LessonKind.FALSE_POSITIVE),
                lesson("CONV_001", LessonKind.FALSE_POSITIVE)));

        assertThat(stat(stats, "CONV_001").falsePositiveRate()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("경계값: 전부 확정이면 오탐률 0%")
    void rateIsZeroWhenAllConfirmed() {
        List<RuleAccuracy.Stat> stats = RuleAccuracy.summarize(List.of(
                lesson("PERF_001", LessonKind.CONFIRMED),
                lesson("PERF_001", LessonKind.CONFIRMED)));

        assertThat(stat(stats, "PERF_001").falsePositiveRate()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("판정(오탐/확정)이 없으면 오탐률은 정의되지 않는다(-1)")
    void rateUndefinedWhenNoDecisions() {
        List<RuleAccuracy.Stat> stats = RuleAccuracy.summarize(List.of(
                lesson("ARCH_003a", LessonKind.MISSED)));

        assertThat(stat(stats, "ARCH_003a").falsePositiveRate()).isEqualTo(-1);
    }

    @Test
    @DisplayName("오탐이 많은 규칙이 먼저 온다 (프롬프트 개선 우선순위)")
    void sortsByFalsePositivesDescending() {
        List<RuleAccuracy.Stat> stats = RuleAccuracy.summarize(List.of(
                lesson("PERF_001", LessonKind.FALSE_POSITIVE),
                lesson("CONV_001", LessonKind.FALSE_POSITIVE),
                lesson("CONV_001", LessonKind.FALSE_POSITIVE)));

        assertThat(stats).extracting(RuleAccuracy.Stat::ruleId).containsExactly("CONV_001", "PERF_001");
    }

    @Test
    @DisplayName("판정이 0건이면 '학습 루프 미완결'을 알리고 기록 방법까지 준다")
    void emptyWhenNoLessons() {
        assertThat(RuleAccuracy.summarize(List.of())).isEmpty();

        String out = RuleAccuracy.render(List.of());

        // "신호 없음"으로만 끝내면 0건이 방치된다 — 무엇을 해야 하는지가 출력에 있어야 한다.
        assertThat(out).contains("0건");
        assertThat(out).contains("reviewLesson");
        assertThat(out).contains("CONFIRMED").contains("FALSE_POSITIVE");
    }
}
