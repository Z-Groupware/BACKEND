package com.module06.backend.capture.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * 자동 분석 길이 하한의 판정을 고정한다.
 *
 * 이 클래스가 생긴 이유가 "관문과 화면이 같은 규칙을 봐야 한다"이므로, 단건(관문)과
 * 배치(화면)가 **같은 답을 낸다**는 것이 가장 중요한 검증이다. 둘이 갈리면 관문은 막는데
 * 화면은 기다리라고 말하는 상태가 다시 생긴다(#572).
 */
class AutoAnalysisLengthGateTest {

    private static final long MEETING = 500L;

    @Test
    @DisplayName("3분 미만 대면 회의는 건너뛴다 — 자동 트리거의 비용 관문이다")
    void 짧은_대면_회의는_건너뛴다() {
        assertThat(gate(Duration.ofSeconds(63), false).tooShortForAutoRun(MEETING)).isTrue();
    }

    @Test
    @DisplayName("경계에서 3분은 통과다")
    void 하한과_같은_길이는_통과한다() {
        assertThat(gate(Duration.ofMinutes(3), false).tooShortForAutoRun(MEETING)).isFalse();
    }

    @Test
    @DisplayName("비대면은 0초여도 면제다 — 길이가 항상 0이라 적용하면 100% 걸린다")
    void 비대면은_하한을_타지_않는다() {
        assertThat(gate(Duration.ZERO, true).tooShortForAutoRun(MEETING)).isFalse();
    }

    @Test
    @DisplayName("대면 0초는 걸린다 — 비대면 면제가 대면까지 열어주면 안 된다")
    void 대면_0초는_걸린다() {
        /*
         * 실제로 이 모양의 회의가 있었다(운영 meetingId=3). started_at 과 ended_at 이 같아
         * 길이가 0 으로 **알려진** 값이다 — 「모름」이 아니므로 통과시키면 안 된다.
         */
        assertThat(gate(Duration.ZERO, false).tooShortForAutoRun(MEETING)).isTrue();
    }

    @Test
    @DisplayName("길이를 모르면 돌린다 — 모르는 것과 짧은 것은 다르다")
    void 길이를_모르면_통과한다() {
        assertThat(new AutoAnalysisLengthGate(meetingId -> Optional.empty())
                .tooShortForAutoRun(MEETING)).isFalse();
    }

    @Test
    @DisplayName("조회가 터져도 돌린다 — DB 가 흔들렸다고 「너무 짧음」으로 기록되면 그 문구가 거짓말이 된다")
    void 조회가_실패하면_통과한다() {
        MeetingLengthProvider exploding = meetingId -> {
            throw new IllegalStateException("커넥션 풀이 말랐다");
        };

        assertThat(new AutoAnalysisLengthGate(exploding).tooShortForAutoRun(MEETING)).isFalse();
    }

    @Test
    @DisplayName("배치가 단건과 같은 답을 낸다 — 이 클래스의 존재 이유다")
    void 배치와_단건이_같은_답을_낸다() {
        AutoAnalysisLengthGate shortOffline = gate(Duration.ofSeconds(63), false);

        assertThat(shortOffline.findTooShortForAutoRun(List.of(MEETING))).containsExactly(MEETING);
        assertThat(shortOffline.tooShortForAutoRun(MEETING)).isTrue();

        AutoAnalysisLengthGate longOffline = gate(Duration.ofMinutes(42), false);

        assertThat(longOffline.findTooShortForAutoRun(List.of(MEETING))).isEmpty();
        assertThat(longOffline.tooShortForAutoRun(MEETING)).isFalse();
    }

    @Test
    @DisplayName("배치 조회가 터져도 비어 있게 답한다 — 못 읽은 것을 「너무 짧음」으로 만들지 않는다")
    void 배치_조회가_실패하면_비어_있다() {
        MeetingLengthProvider exploding = new MeetingLengthProvider() {
            @Override
            public Optional<Duration> actualLengthOf(long meetingId) {
                return Optional.empty();
            }

            @Override
            public java.util.Map<Long, MeetingLength> lengthsOf(List<Long> meetingIds) {
                throw new IllegalStateException("커넥션 풀이 말랐다");
            }
        };

        assertThat(new AutoAnalysisLengthGate(exploding).findTooShortForAutoRun(List.of(MEETING))).isEmpty();
    }

    @Test
    @DisplayName("빈 입력은 조회하지 않는다")
    void 빈_입력은_조회하지_않는다() {
        MeetingLengthProvider forbidden = new MeetingLengthProvider() {
            @Override
            public Optional<Duration> actualLengthOf(long meetingId) {
                throw new AssertionError("빈 입력에 조회하면 안 된다");
            }

            @Override
            public java.util.Map<Long, MeetingLength> lengthsOf(List<Long> meetingIds) {
                throw new AssertionError("빈 입력에 조회하면 안 된다");
            }
        };

        assertThat(new AutoAnalysisLengthGate(forbidden).findTooShortForAutoRun(List.of())).isEmpty();
    }

    private AutoAnalysisLengthGate gate(Duration length, boolean online) {
        return new AutoAnalysisLengthGate(new MeetingLengthProvider() {
            @Override
            public Optional<Duration> actualLengthOf(long meetingId) {
                return Optional.of(length);
            }

            @Override
            public Optional<Boolean> isOnline(long meetingId) {
                return Optional.of(online);
            }
        });
    }
}
