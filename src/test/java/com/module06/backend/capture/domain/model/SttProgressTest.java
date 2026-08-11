package com.module06.backend.capture.domain.model;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.domain.model.SttProgress.BlockTiming;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 받아쓰기 진행도와 남은 시간 추정(CAP-06).
 *
 * <p>검증의 축은 <b>못 재는 것을 지어내지 않는가</b>다. 0 은 "곧 끝난다"로 읽히고 null 은
 * "아직 못 잰다"다 — 둘을 섞으면 화면이 근거 없는 숫자를 자신 있게 보여준다.
 */
class SttProgressTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 15, 0, 0);
    /* 블록 하나는 10분 오디오다. */
    private static final int TEN_MINUTES_MS = 600_000;

    @Test
    @DisplayName("블록 집계를 센다 — total·done·failed")
    void 블록을_집계한다() {
        SttProgress progress = SttProgress.of(List.of(
                done(TEN_MINUTES_MS, 60),
                done(TEN_MINUTES_MS, 60),
                failed(),
                queued()), NOW);

        assertThat(progress.total()).isEqualTo(4);
        assertThat(progress.done()).isEqualTo(2);
        assertThat(progress.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("이 회의의 실측 처리율로 남은 시간을 잰다 — 상수를 박지 않는다")
    void 실측으로_남은_시간을_잰다() {
        /*
         * 10분(600초) 오디오를 60초에 끝냈다 → 오디오 1초당 0.1초. 남은 10분이면 60초다.
         * 상수를 박으면 계정 동시 실행 수·리전 부하에 따라 매번 틀린 숫자를 보여준다.
         */
        SttProgress progress = SttProgress.of(List.of(
                done(TEN_MINUTES_MS, 60),
                queued()), NOW);

        assertThat(progress.estimatedRemainingSec()).isEqualTo(60);
    }

    @Test
    @DisplayName("⚠ 끝난 블록이 없으면 null 이다 — 0 도 큰 수도 지어내지 않는다")
    void 잴_근거가_없으면_null이다() {
        SttProgress progress = SttProgress.of(List.of(queued(), queued()), NOW);

        /*
         * 0 을 주면 "곧 끝난다"로 읽히고, 큰 수를 주면 근거 없는 겁주기다. 명세의 이 필드가
         * 오래 비어 있던 이유가 그것이다(「지어낼 방법이 없다」).
         */
        assertThat(progress.estimatedRemainingSec()).isNull();
    }

    @Test
    @DisplayName("남은 블록이 없으면 0 이다 — 이건 측정 문제가 아니라 사실이다")
    void 남은_블록이_없으면_0이다() {
        SttProgress progress = SttProgress.of(List.of(
                done(TEN_MINUTES_MS, 60),
                failed()), NOW);

        assertThat(progress.estimatedRemainingSec()).isZero();
    }

    @Test
    @DisplayName("돌고 있는 블록은 이미 진행한 만큼을 뺀다 — 안 빼면 남은 시간이 줄지 않는다")
    void 도는_중인_블록의_진행분을_뺀다() {
        /*
         * 10분 오디오를 60초에 끝낸 실측(0.1배). 두 번째 블록이 9분 전에 시작됐으면 남은
         * 오디오는 1분이고 추정은 6초다. 빼지 않으면 10분치가 남은 것으로 세어져 화면의 남은
         * 시간이 끝날 때까지 줄지 않고, 사람은 멈춘 진행 표시를 고장으로 읽는다.
         */
        SttProgress progress = SttProgress.of(List.of(
                done(TEN_MINUTES_MS, 60),
                running(TEN_MINUTES_MS, NOW.minusMinutes(9))), NOW);

        assertThat(progress.estimatedRemainingSec()).isEqualTo(6);
    }

    @Test
    @DisplayName("예상보다 오래 걸린 블록은 0 으로 접는다 — 음수가 남으면 전체 추정이 짧아진다")
    void 초과_진행분은_0으로_접는다() {
        SttProgress progress = SttProgress.of(List.of(
                done(TEN_MINUTES_MS, 60),
                // 오디오 길이(10분)보다 오래 돌고 있다.
                running(TEN_MINUTES_MS, NOW.minusMinutes(30)),
                queued()), NOW);

        // 초과분이 음수로 남아 세 번째 블록의 남은 시간을 상계하면 안 된다.
        assertThat(progress.estimatedRemainingSec()).isEqualTo(60);
    }

    @Test
    @DisplayName("실패한 블록의 소요는 실측에 넣지 않는다 — 중간에 끊긴 시간은 처리 속도가 아니다")
    void 실패한_블록은_실측에서_뺀다() {
        /*
         * FAILED 를 섞으면 비율이 실제보다 짧거나 길게 왜곡된다. 여기서는 실측 재료가
         * DONE 하나뿐이므로 그 비율(0.1배)만 쓰여야 한다.
         */
        SttProgress progress = SttProgress.of(List.of(
                done(TEN_MINUTES_MS, 60),
                failedAfter(TEN_MINUTES_MS, 600),
                queued()), NOW);

        assertThat(progress.estimatedRemainingSec()).isEqualTo(60);
    }

    @Test
    @DisplayName("블록이 아예 없으면 total 0 · null 이다 — 0 초는 「끝났다」로 읽힌다")
    void 블록이_없으면_null이다() {
        SttProgress progress = SttProgress.of(List.of(), NOW);

        assertThat(progress.total()).isZero();
        assertThat(progress.estimatedRemainingSec()).isNull();
        assertThat(SttProgress.of(null, NOW).total()).isZero();
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private static BlockTiming done(int audioMs, int wallSeconds) {
        LocalDateTime startedAt = NOW.minusHours(1);
        return new BlockTiming(SttBlockStatus.DONE, audioMs, startedAt, startedAt.plusSeconds(wallSeconds));
    }

    private static BlockTiming failed() {
        return new BlockTiming(SttBlockStatus.FAILED, TEN_MINUTES_MS, NOW.minusHours(1), NOW.minusHours(1));
    }

    private static BlockTiming failedAfter(int audioMs, int wallSeconds) {
        LocalDateTime startedAt = NOW.minusHours(1);
        return new BlockTiming(SttBlockStatus.FAILED, audioMs, startedAt, startedAt.plusSeconds(wallSeconds));
    }

    private static BlockTiming queued() {
        return new BlockTiming(SttBlockStatus.QUEUED, TEN_MINUTES_MS, null, null);
    }

    private static BlockTiming running(int audioMs, LocalDateTime startedAt) {
        return new BlockTiming(SttBlockStatus.RUNNING, audioMs, startedAt, null);
    }
}
