package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 생략 흔적 검증 — 키 없이 돌아간다(라이브 호출 없음).
 *
 * <p>지켜야 하는 것은 하나다: <b>초록불이 '판정 통과'와 '판정 미수행'을 구분할 수 있어야 한다.</b>
 * 구분이 없으면 크레딧이 소진된 며칠 동안 리뷰 없이 나간 변경을 아무도 세지 못한다 —
 * 이 저장소가 감사 로그 0건으로 이미 한 번 겪은 실패다.
 */
class GateSkipRecorderTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-05T05:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    @Test
    @DisplayName("생략은 감사 로그에 skipReason 행으로 남는다 — 판정 라운드와 구분된다")
    void 생략은_감사_로그에_남는다() throws IOException {
        Path log = dir.resolve("logs/error_log.jsonl");

        GateSkipRecorder.appendAudit(log, FIXED, "제공자를 쓸 수 없다 (HTTP 429 …)", 7);

        AuditRecord written = new ObjectMapper().readValue(Files.readString(log).strip(), AuditRecord.class);
        assertThat(written.isSkipped()).isTrue();
        assertThat(written.skipReason()).contains("429");
        assertThat(written.unreviewedFiles()).isEqualTo(7);
    }

    @Test
    @DisplayName("생략 행에 그럴듯한 판정값을 넣지 않는다 — 통과한 판정과 섞이면 집계가 무너진다")
    void 생략_행은_판정값을_비운다() {
        AuditRecord skipped = AuditRecord.skipped("2026-08-05T14:00", "크레딧 소진", 3);

        // score 0 · PASS 같은 기본값을 넣으면 "0점으로 통과한 라운드"처럼 읽힌다.
        assertThat(skipped.decision()).isNull();
        assertThat(skipped.model()).isNull();
        assertThat(skipped.round()).isZero();
    }

    @Test
    @DisplayName("판정 라운드는 skipReason 이 비어 있다 — 기존 호출부는 그대로다")
    void 판정_라운드는_생략이_아니다() {
        AuditRecord round = new AuditRecord("2026-08-05T14:00", 1, "gemini", 85,
                false, JudgeDecision.PASS, 1, false);

        assertThat(round.isSkipped()).isFalse();
        assertThat(round.unreviewedFiles()).isZero();
    }

    @Test
    @DisplayName("직렬화한 줄을 다시 읽을 수 있다 — 파생 메서드가 미지 속성으로 새 나가면 안 된다")
    void 왕복이_깨지지_않는다() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        AuditRecord skipped = AuditRecord.skipped("2026-08-05T14:00", "크레딧 소진", 3);

        String json = mapper.writeValueAsString(skipped);

        // isSkipped()/unreviewedFiles() 가 @JsonIgnore 없이 새 나가면 여기서 미지 속성으로 터진다.
        // 그 경우 ReviewReport 는 '손상된 줄'로 조용히 건너뛰어, 기록은 남았는데 리포트에서만 사라진다.
        assertThat(json).doesNotContain("\"skipped\"").doesNotContain("\"unreviewedFiles\"");
        assertThat(mapper.readValue(json, AuditRecord.class)).isEqualTo(skipped);
    }

    @Test
    @DisplayName("skipReason 이 없던 옛 줄도 읽힌다 — append-only 로그의 과거 기록이 깨지면 안 된다")
    void 필드가_없던_옛_줄도_읽힌다() throws IOException {
        String legacy = "{\"timestamp\":\"2026-07-14T00:00:00\",\"round\":1,\"model\":\"gemini\","
                + "\"score\":85,\"hasCritical\":false,\"decision\":\"PASS\","
                + "\"findingsCount\":1,\"terminatedByBudget\":false}";

        AuditRecord parsed = new ObjectMapper().readValue(legacy, AuditRecord.class);

        assertThat(parsed.isSkipped()).isFalse();
        assertThat(parsed.decision()).isEqualTo(JudgeDecision.PASS);
    }

    @Test
    @DisplayName("배너는 '통과'가 아니라 '미수행'이라고 말한다 — 문구가 이 변경의 전부다")
    void 배너는_미수행을_말한다() {
        String banner = GateSkipRecorder.reviewBanner("제공자를 쓸 수 없다 (HTTP 429 …)", 7);

        assertThat(banner).startsWith("> [!WARNING]");   // 스텝 요약 최상단에 경고로 렌더된다
        assertThat(banner).contains("판정 미수행").contains("7개").contains("429");
    }

    @Test
    @DisplayName("건수를 셀 수 없는 생략은 0개로 위장하지 않는다")
    void 셀_수_없으면_그렇게_적는다() {
        String banner = GateSkipRecorder.reviewBanner("사유", GateSkipRecorder.UNKNOWN_COUNT);

        assertThat(banner).contains("셀 수 없음").doesNotContain("-1개");
    }

    @Test
    @DisplayName("스모크 배너는 리뷰 배너와 문구가 다르다 — 둘을 혼동하면 잘못된 안심을 준다")
    void 스모크_배너는_리뷰_배너와_다르다() {
        String smoke = GateSkipRecorder.smokeBanner("제공자를 쓸 수 없다 (HTTP 429 …)");

        // 이 잡은 PR 코드를 리뷰하지 않는다. "리뷰를 받지 않았다"고 쓰면 반대로 읽힌다.
        assertThat(smoke).contains("회귀 감시").doesNotContain("리뷰를 받지 않았다");
    }

    @Test
    @DisplayName("배너는 기존 요약을 덮지 않고 덧붙인다 — 다른 스텝의 요약과 공존한다")
    void 배너는_덧붙인다() throws IOException {
        Path summary = Files.writeString(dir.resolve("summary.md"), "## 앞선 스텝 요약\n");

        GateSkipRecorder.writeBanner(summary, GateSkipRecorder.reviewBanner("사유", 1));

        assertThat(Files.readString(summary)).startsWith("## 앞선 스텝 요약").contains("[!WARNING]");
    }

    @Test
    @DisplayName("기록 실패는 게이트를 망가뜨리지 않는다 — 신호가 목적이지 게이트가 아니다")
    void 기록_실패는_삼킨다() throws IOException {
        Path blocker = Files.writeString(dir.resolve("blocker"), "x");   // 파일을 부모로 → IO 실패
        Path impossible = blocker.resolve("child/log.jsonl");

        assertThatCode(() -> GateSkipRecorder.appendAudit(impossible, FIXED, "사유", 1))
                .doesNotThrowAnyException();
        assertThatCode(() -> GateSkipRecorder.writeBanner(impossible, "배너"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("리포트가 생략을 따로 세어 보여준다 — 라운드 표에 묻히면 아무도 못 본다")
    void 리포트가_생략을_집계한다() {
        String md = ReviewReport.render(List.of(
                new AuditRecord("t1", 1, "gemini", 85, false, JudgeDecision.PASS, 1, false),
                AuditRecord.skipped("t2", "크레딧 소진", 4),
                AuditRecord.skipped("t3", "크레딧 소진", 2)), List.of());

        assertThat(md).contains("판정 미수행").contains("2회 생략").contains("누적 6개");
        // 생략 행이 라운드 표에 섞이면 "3라운드 돌았다"가 된다 — 실제로 돈 것은 1라운드다.
        assertThat(md).contains("1라운드");
    }

    @Test
    @DisplayName("생략이 0건이면 그 절을 만들지 않는다 — 상시 표시되면 배경이 되어 안 보인다")
    void 생략이_없으면_절도_없다() {
        String md = ReviewReport.render(List.of(
                new AuditRecord("t1", 1, "gemini", 100, false, JudgeDecision.PASS, 0, false)), List.of());

        assertThat(md).doesNotContain("판정 미수행");
    }
}
