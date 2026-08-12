package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 최적화 루프 지표 — 세 비율의 정의와 '조치가 함께 나오는지'를 고정한다.
 * 지표는 사람이 손잡이를 돌리는 근거이므로, 정의가 조용히 바뀌면 잘못된 손잡이를 돌리게 된다.
 */
class LoopMetricsTest {

    private static final int THRESHOLD = 80;

    private AuditRecord judged(int round, int score, int findings) {
        return new AuditRecord("2026-08-12T0" + round + ":00:00", round, "gemini",
                score, false, JudgeDecision.PASS, findings, false);
    }

    private AuditRecord skipped(String at, int unreviewed) {
        return AuditRecord.skipped(at, GateSkipRecorder.NO_API_KEY, unreviewed);
    }

    private Lesson lesson(String rule, LessonKind kind) {
        return new Lesson("2026-08-12T00:00:00", rule, kind, "note");
    }

    private LoopMetrics.Snapshot measure(List<AuditRecord> records, List<Lesson> lessons) {
        return LoopMetrics.measure("2026-08-12T12:00:00", records, lessons, THRESHOLD);
    }

    @Test
    @DisplayName("커버리지 분모는 '실행'이 아니라 .java 개수 — 단위를 섞으면 실제보다 좋게 나온다")
    void coverageCountsFilesNotRuns() {
        // 판정 1건(파일 1개) + 생략 1회(파일 20개 미리뷰). 실행 단위로 세면 50%,
        // 파일 단위로 세면 1/21 ≈ 5%. 후자가 "변경이 리뷰를 받았나"의 실제 답이다.
        LoopMetrics.Snapshot s = measure(List.of(judged(1, 100, 0), skipped("2026-08-12T09:00:00", 20)), List.of());

        assertThat(s.judged()).isEqualTo(1);
        assertThat(s.unreviewed()).isEqualTo(20);
        assertThat(s.skipRuns()).isEqualTo(1);
        assertThat(s.coverage()).isCloseTo(1.0 / 21, within(1e-9));
    }

    @Test
    @DisplayName("건수를 셀 수 없는 생략(-1)은 누적을 줄이지 않고 따로 센다")
    void uncountableSkipsDoNotShrinkTheTotal() {
        LoopMetrics.Snapshot s = measure(List.of(
                judged(1, 100, 0),
                skipped("2026-08-12T09:00:00", 5),
                skipped("2026-08-12T09:30:00", GateSkipRecorder.UNKNOWN_COUNT)), List.of());

        // -1을 그대로 더하면 4가 된다 — 모르는 것을 '음수 파일'로 세면 커버리지가 부풀려진다.
        assertThat(s.unreviewed()).isEqualTo(5);
        assertThat(s.uncountableSkips()).isEqualTo(1);
        assertThat(s.skipRuns()).isEqualTo(2);
    }

    @Test
    @DisplayName("판정 수율 = finding을 낸 판정 ÷ 전체 판정 (finding 건수가 아니라 판정 수 기준)")
    void yieldCountsJudgementsThatProducedSignal() {
        // 판정 4회 중 신호 1회. finding이 3건이어도 수율은 1/4 — 한 파일에 몰린 지적이
        // '호출 1회당 신호 확률'을 올린 것처럼 보이면 대상 선정을 잘못 판단한다.
        LoopMetrics.Snapshot s = measure(List.of(
                judged(1, 100, 0), judged(2, 100, 0), judged(3, 100, 0), judged(4, 55, 3)), List.of());

        assertThat(s.judged()).isEqualTo(4);
        assertThat(s.withFinding()).isEqualTo(1);
        assertThat(s.findings()).isEqualTo(3);
        assertThat(s.belowThreshold()).isEqualTo(1);       // score 55 < 80
        assertThat(s.yield()).isCloseTo(0.25, within(1e-9));
    }

    @Test
    @DisplayName("학습 전환율 = 사람이 판정한 finding ÷ 전체 finding (MISSED는 finding에서 나온 게 아니라 제외)")
    void closureUsesHumanReviewedLessonsOnly() {
        LoopMetrics.Snapshot s = measure(
                List.of(judged(1, 70, 4)),
                List.of(lesson("CONV_001", LessonKind.CONFIRMED),
                        lesson("CONV_001", LessonKind.FALSE_POSITIVE),
                        lesson("PERF_001", LessonKind.MISSED)));   // Judge가 놓친 것 — finding이 아니었다

        assertThat(s.findings()).isEqualTo(4);
        assertThat(s.humanJudged()).isEqualTo(2);
        assertThat(s.falsePositives()).isEqualTo(1);
        assertThat(s.closure()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("기록이 없으면 비율은 정의되지 않는다(-1) — 0%로 보고하면 없는 문제를 고치게 된다")
    void ratesUndefinedWithoutData() {
        LoopMetrics.Snapshot s = measure(List.of(), List.of());

        assertThat(s.coverage()).isEqualTo(-1);
        assertThat(s.yield()).isEqualTo(-1);
        assertThat(s.closure()).isEqualTo(-1);
        assertThat(LoopMetrics.actions(s, List.of())).isEmpty();   // 판단 근거가 없으면 조치도 없다
    }

    @Test
    @DisplayName("임계를 넘기면 '돌릴 손잡이'가 함께 나온다 — 숫자만 있는 리포트는 아무것도 바꾸지 않는다")
    void eachViolationNamesTheKnob() {
        // 판정 6회 중 신호 1회(수율 17%) · 리뷰 못 한 .java 10개(커버리지 38%) · finding 4건에 사람 판정 1건(25%)
        LoopMetrics.Snapshot s = measure(
                List.of(judged(1, 100, 0), judged(2, 100, 0), judged(3, 100, 0),
                        judged(4, 100, 0), judged(5, 100, 0), judged(6, 60, 4),
                        skipped("2026-08-12T09:00:00", 10)),
                List.of(lesson("CONV_001", LessonKind.CONFIRMED)));

        List<String> actions = LoopMetrics.actions(s, RuleAccuracy.summarize(List.of(
                lesson("PERF_001", LessonKind.FALSE_POSITIVE),
                lesson("PERF_001", LessonKind.FALSE_POSITIVE),
                lesson("PERF_001", LessonKind.CONFIRMED))));   // 오탐률 67% > 50%

        assertThat(actions).hasSize(4);
        assertThat(actions.get(0)).contains("커버리지").contains("SETUP.md");     // 리뷰 못 받은 코드가 먼저
        assertThat(actions.get(1)).contains("수율").contains("rules.yaml");
        assertThat(actions.get(2)).contains("전환율").contains("reviewLesson");
        assertThat(actions.get(3)).contains("PERF_001").contains("오탐률");
    }

    @Test
    @DisplayName("finding은 나오는데 임계 미달이 0건이면 '수정 라운드가 발동한 적 없다'를 지목한다")
    void flagsAFixLoopThatNeverFires() {
        // 감점 15 · 임계 80 → 단일 지적은 85점 PASS. 요청서는 남지만 재수정 라운드는 시작되지 않는다.
        // 지표 없이는 "게이트가 돌고 있다"와 구분되지 않는 상태다.
        LoopMetrics.Snapshot s = measure(List.of(judged(1, 85, 1), judged(2, 85, 1)), List.of());

        assertThat(s.belowThreshold()).isZero();
        assertThat(LoopMetrics.actions(s, List.of()))
                .anySatisfy(a -> assertThat(a).contains("NEEDS_REVISION").contains("pass_threshold"));
    }

    @Test
    @DisplayName("임계 미달 판정이 있으면 그 진단은 뜨지 않는다 — 수정 경로가 실제로 돌았다는 뜻")
    void noFixLoopWarningWhenThresholdWasCrossed() {
        LoopMetrics.Snapshot s = measure(List.of(judged(1, 70, 2)), List.of());

        assertThat(LoopMetrics.actions(s, List.of()))
                .noneSatisfy(a -> assertThat(a).contains("NEEDS_REVISION"));
    }

    @Test
    @DisplayName("델타 칸 — 이전 기준선이 없으면 비우고, 있으면 그 값을 보여준다")
    void rendersPreviousColumnOnlyWhenBaselineExists() {
        LoopMetrics.Snapshot before = measure(List.of(judged(1, 100, 0), skipped("2026-08-11T09:00:00", 3)), List.of());
        LoopMetrics.Snapshot after = measure(List.of(judged(1, 100, 0), judged(2, 100, 0)), List.of());

        // 커버리지 25% → 100%. 이전 칸에 옛 값이 남아야 '고쳐진 것'을 확인할 수 있다.
        assertThat(before.coverage()).isCloseTo(0.25, within(1e-9));
        assertThat(LoopMetrics.render(after, before, List.of(), THRESHOLD)).contains("25%");
        assertThat(LoopMetrics.render(after, null, List.of(), THRESHOLD))
                .contains("커버리지")
                .doesNotContain("25%");
    }

    @Test
    @DisplayName("계산하지 않기로 한 지표를 출력이 명시한다 — round 필드는 러너마다 뜻이 다르다")
    void statesTheInstrumentationLimit() {
        String out = LoopMetrics.render(measure(List.of(judged(1, 100, 0)), List.of()), null, List.of(), THRESHOLD);

        // 이 문구가 사라지면 누군가 round 평균을 '수렴 라운드'로 계산해 예산 캡을 조정한다.
        assertThat(out).contains("수렴 라운드").contains("round");
    }

    @Test
    @DisplayName("기준선 왕복 — 원시 카운터만 적고, 미지 속성이 섞여도 읽힌다(필드가 느는 이력 파일)")
    void baselineRoundTripIgnoresUnknownFields(@TempDir Path dir) throws IOException {
        Path baseline = dir.resolve("loop-metrics.jsonl");
        assertThat(LoopMetrics.lastSnapshot(baseline)).isNull();   // 첫 실행

        LoopMetrics.appendSnapshot(baseline, measure(List.of(judged(1, 100, 0)), List.of()));
        // 나중 버전이 붙일 새 필드를 흉내낸다 — 엄격히 읽으면 이력 전체를 못 읽게 된다.
        Files.writeString(baseline,
                "{\"timestamp\":\"2026-08-12T13:00:00\",\"judged\":9,\"futureField\":1}" + System.lineSeparator(),
                java.nio.file.StandardOpenOption.APPEND);

        LoopMetrics.Snapshot last = LoopMetrics.lastSnapshot(baseline);
        assertThat(last).isNotNull();
        assertThat(last.judged()).isEqualTo(9);
        // 비율은 파일에 적지 않는다 — 정의를 바꿨을 때 과거 줄의 뜻이 조용히 달라지는 것을 막는다.
        assertThat(Files.readString(baseline)).doesNotContain("coverage").doesNotContain("yield");
    }

    @Test
    @DisplayName("최신 기준선은 '마지막 줄'이 아니라 timestamp가 가장 큰 줄 — union 머지가 순서를 흔든다")
    void latestBaselineIsByTimestampNotFilePosition(@TempDir Path dir) throws IOException {
        Path baseline = dir.resolve("loop-metrics.jsonl");
        // merge=union 은 "내 쪽 먼저, 상대 쪽 나중"으로 붙일 뿐 시각을 보지 않는다 →
        // 머지 직후 파일 끝에 옛 줄이 올 수 있다. 그걸 최신으로 보면 개선을 악화로 읽는다.
        Files.writeString(baseline, """
                {"timestamp":"2026-08-12T12:00:00","judged":100}
                {"timestamp":"2026-08-05T09:00:00","judged":3}
                """);

        assertThat(LoopMetrics.lastSnapshot(baseline).judged()).isEqualTo(100);
    }
}
