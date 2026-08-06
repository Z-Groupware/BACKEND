package com.module06.backend.meeting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * CAP-01 캡처 세션 도메인의 최초 상태와 입력 불변식을 검증한다.
 */
@DisplayName("CAP-01 캡처 세션 도메인")
class CaptureSessionTest {

    /* 신규 세션이 D 소유 값만 가진 ACTIVE 상태로 생성되는지 검증한다. */
    @Test
    @DisplayName("host와 시간축 기준점으로 ACTIVE 세션을 생성한다")
    void startsActiveCaptureSession() {
        /* KST 시작 일시와 동일 순간의 epoch 밀리초를 준비한다. */
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 6, 14, 0);
        long startedAtEpochMs = startedAt
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant()
                .toEpochMilli();

        /* fixture 자체가 문서화한 2026-08-06 14:00 KST 순간과 일치하는지도 고정값으로 확인한다. */
        assertThat(startedAtEpochMs).isEqualTo(1_785_992_400_000L);

        /* 91번 회의의 host 3번이 신규 캡처 세션을 시작한다. */
        CaptureSession captureSession = CaptureSession.start(91L, 3L, startedAt, startedAtEpochMs);

        /* 저장 전 ID는 없고 최초 상태와 D 소유 시간 값은 요청대로 설정돼야 한다. */
        assertThat(captureSession.getId()).isNull();
        assertThat(captureSession.getMeetingId()).isEqualTo(91L);
        assertThat(captureSession.getStartedBy()).isEqualTo(3L);
        assertThat(captureSession.getStatus()).isEqualTo(CaptureSessionStatus.ACTIVE);
        assertThat(captureSession.isPaused()).isFalse();
        assertThat(captureSession.getStartedAt()).isEqualTo(startedAt);
        assertThat(captureSession.getStartedAtEpochMs()).isEqualTo(startedAtEpochMs);
        assertThat(captureSession.getPausedAt()).isNull();
        assertThat(captureSession.getEndedAt()).isNull();
    }

    /* 식별자와 시간축이 잘못된 세션이 도메인 원본으로 만들어지지 않는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 회의·시작자·시각을 거절한다")
    void rejectsInvalidStartValues() {
        /* null 회의와 양수가 아닌 시작자는 식별 가능한 세션을 만들 수 없다. */
        assertThatThrownBy(() -> CaptureSession.start(null, 3L, LocalDateTime.now(), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CaptureSession.start(91L, 0L, LocalDateTime.now(), 1L))
                .isInstanceOf(IllegalArgumentException.class);

        /* null 로컬 시각과 음수 epoch는 공통 시간축 기준으로 사용할 수 없다. */
        assertThatThrownBy(() -> CaptureSession.start(91L, 3L, null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CaptureSession.start(91L, 3L, LocalDateTime.now(), -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* ACTIVE 세션이 같은 식별자와 시간축을 유지한 채 PAUSED로 전이되는지 검증한다. */
    @Test
    @DisplayName("ACTIVE 세션을 PAUSED 상태로 전이한다")
    void pausesActiveCaptureSession() {
        /* 14시에 시작한 저장된 ACTIVE 세션과 14시 31분 일시정지 시각을 준비한다. */
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 6, 14, 0);
        LocalDateTime pausedAt = LocalDateTime.of(2026, 8, 6, 14, 31, 8);
        CaptureSession activeSession = CaptureSession.reconstitute(
                15L,
                91L,
                3L,
                CaptureSessionStatus.ACTIVE,
                startedAt,
                1_785_992_400_000L,
                null,
                null,
                startedAt,
                startedAt
        );

        /* CAP-02 도메인 전이를 실행한다. */
        CaptureSession pausedSession = activeSession.pause(pausedAt);

        /* 세션 식별자·시간축은 유지되고 상태·pausedAt·updatedAt만 바뀌어야 한다. */
        assertThat(pausedSession.getId()).isEqualTo(15L);
        assertThat(pausedSession.getMeetingId()).isEqualTo(91L);
        assertThat(pausedSession.getStartedBy()).isEqualTo(3L);
        assertThat(pausedSession.getStatus()).isEqualTo(CaptureSessionStatus.PAUSED);
        assertThat(pausedSession.isPaused()).isTrue();
        assertThat(pausedSession.getStartedAt()).isEqualTo(startedAt);
        assertThat(pausedSession.getStartedAtEpochMs()).isEqualTo(1_785_992_400_000L);
        assertThat(pausedSession.getPausedAt()).isEqualTo(pausedAt);
        assertThat(pausedSession.getUpdatedAt()).isEqualTo(pausedAt);
    }

    /* 잘못된 시각과 PAUSED·ENDED 상태의 중복 전이가 도메인에서 차단되는지 검증한다. */
    @Test
    @DisplayName("잘못된 일시정지 시각과 PAUSED·ENDED 재전이를 거절한다")
    void rejectsInvalidPauseTransitions() {
        /* 정상 ACTIVE 세션과 시작 이후 일시정지 시각을 준비한다. */
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 6, 14, 0);
        LocalDateTime pausedAt = LocalDateTime.of(2026, 8, 6, 14, 31, 8);
        CaptureSession activeSession = CaptureSession.reconstitute(
                15L,
                91L,
                3L,
                CaptureSessionStatus.ACTIVE,
                startedAt,
                1_785_992_400_000L,
                null,
                null,
                startedAt,
                startedAt
        );

        /* 시작 전 시각과 null은 유효한 CAP-02 전이 시각이 아니다. */
        assertThatThrownBy(() -> activeSession.pause(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> activeSession.pause(startedAt.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);

        /* 이미 PAUSED인 세션의 재호출과 ENDED 세션의 상태 역행을 차단한다. */
        CaptureSession pausedSession = activeSession.pause(pausedAt);
        assertThatThrownBy(() -> pausedSession.pause(pausedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);

        CaptureSession endedSession = CaptureSession.reconstitute(
                15L,
                91L,
                3L,
                CaptureSessionStatus.ENDED,
                startedAt,
                1_785_992_400_000L,
                pausedAt,
                pausedAt.plusMinutes(20),
                startedAt,
                pausedAt.plusMinutes(20)
        );
        assertThatThrownBy(() -> endedSession.pause(pausedAt.plusMinutes(21)))
                .isInstanceOf(IllegalStateException.class);
    }
}
