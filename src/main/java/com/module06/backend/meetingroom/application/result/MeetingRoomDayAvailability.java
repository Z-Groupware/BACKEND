package com.module06.backend.meetingroom.application.result;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/*
 * ROOM-02 주간 응답에서 평일 하루의 30분 슬롯 현황을 표현하는 애플리케이션 결과다.
 *
 * @param date 슬롯이 속한 날짜
 * @param dayOfWeek 날짜의 요일
 * @param slots 회의실 이용 가능 시간을 30분으로 분할한 슬롯 현황
 */
public record MeetingRoomDayAvailability(
        LocalDate date,
        DayOfWeek dayOfWeek,
        List<MeetingRoomSlotSummary> slots
) {

    /* 날짜별 슬롯 목록을 불변으로 복사해 결과 생성 이후 변경되지 않도록 보호한다. */
    public MeetingRoomDayAvailability {
        /* 예약이 없는 날에도 전체 AVAILABLE 슬롯 목록을 안전한 불변 값으로 유지한다. */
        slots = List.copyOf(slots);
    }
}
