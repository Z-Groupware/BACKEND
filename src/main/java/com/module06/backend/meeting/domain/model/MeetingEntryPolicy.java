package com.module06.backend.meeting.domain.model;

import java.time.LocalDateTime;

/*
 * MEET-03과 MEET-07이 공유하는 회의 입장 허용 시간 정책이다.
 *
 * 참석자는 예약 시작 10분 전부터 예약 종료 시각까지 입장할 수 있다.
 */
public final class MeetingEntryPolicy {

    /* 예약 시작 전에 허용하는 조기 입장 시간이다. */
    public static final int EARLY_ENTRY_MINUTES = 10;

    /* 상태 없는 정책 객체를 생성하지 못하도록 생성자를 숨긴다. */
    private MeetingEntryPolicy() {
    }

    /* 현재 시각이 시작 10분 전과 종료 시각을 포함한 입장 허용 구간인지 판단한다. */
    public static boolean isEntryAvailable(
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime now
    ) {
        /* 허용 구간의 두 경계는 모두 포함해 정확한 경계 시각에도 입장을 허용한다. */
        LocalDateTime entryOpensAt = startAt.minusMinutes(EARLY_ENTRY_MINUTES);
        return !now.isBefore(entryOpensAt) && !now.isAfter(endAt);
    }
}
