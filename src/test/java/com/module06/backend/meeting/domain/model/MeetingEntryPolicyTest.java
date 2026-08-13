package com.module06.backend.meeting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * MEET-03과 CAP-01이 공유하는 회의 시작 허용 시간 경계 규칙을 검증한다.
 */
@DisplayName("회의 입장 허용 시간 정책")
class MeetingEntryPolicyTest {

    /* 예약 시작 10분 전과 종료 시각을 포함한 구간만 허용되는지 검증한다. */
    @Test
    @DisplayName("시작 10분 전부터 종료 시각까지 입장을 허용한다")
    void allowsEntryInsideInclusiveWindow() {
        /* 14시부터 15시까지 예약된 회의의 두 시간 경계를 준비한다. */
        LocalDateTime startAt = LocalDateTime.of(2026, 8, 6, 14, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 8, 6, 15, 0);

        /* 시작 10분 전과 종료 시각 자체는 포함 경계이므로 모두 허용돼야 한다. */
        assertThat(MeetingEntryPolicy.isEntryAvailable(
                startAt,
                endAt,
                LocalDateTime.of(2026, 8, 6, 13, 50)
        )).isTrue();
        assertThat(MeetingEntryPolicy.isEntryAvailable(
                startAt,
                endAt,
                LocalDateTime.of(2026, 8, 6, 15, 0)
        )).isTrue();

        /* 허용 시작보다 1초 이르거나 종료보다 1초 늦으면 입장 버튼이 비활성화돼야 한다. */
        assertThat(MeetingEntryPolicy.isEntryAvailable(
                startAt,
                endAt,
                LocalDateTime.of(2026, 8, 6, 13, 49, 59)
        )).isFalse();
        assertThat(MeetingEntryPolicy.isEntryAvailable(
                startAt,
                endAt,
                LocalDateTime.of(2026, 8, 6, 15, 0, 1)
        )).isFalse();
    }
}
