package com.module06.backend.meetingroom.domain.model;

import java.time.LocalDateTime;

/*
 * ROOM-04 이용 시간 축소 검증에 필요한 미래 예약의 최소 시간 정보다.
 */
public record ScheduledMeetingReservation(
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
