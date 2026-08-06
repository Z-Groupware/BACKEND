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
}
