package com.module06.backend.meetingroom.application.result;

import java.time.LocalDate;
import java.util.List;

/*
 * ROOM-02 회의실 예약 현황 조회 결과 전체를 표현하는 애플리케이션 결과 객체다.
 *
 * 단일 회의실의 월요일부터 금요일까지 시간표를 한 번에 그릴 수 있도록
 * 주간 범위, 회의실 정보, 날짜별 슬롯 현황을 함께 제공한다.
 *
 * @param weekStart 조회 주의 월요일
 * @param weekEnd 조회 주의 금요일
 * @param slotMinutes 슬롯 하나의 길이(분)
 * @param meetingRoom 조회 대상 회의실 정보
 * @param days 월요일부터 금요일까지 날짜별 슬롯 현황
 */
public record MeetingRoomAvailability(
        LocalDate weekStart,
        LocalDate weekEnd,
        int slotMinutes,
        MeetingRoomAvailabilitySummary meetingRoom,
        List<MeetingRoomDayAvailability> days
) {

    /*
     * 날짜별 현황 목록을 불변으로 복사해 결과 생성 이후 변경되지 않도록 보호한다.
     *
     * @param days 월요일부터 금요일까지 날짜별 슬롯 현황
     */
    public MeetingRoomAvailability {
        /* 주간 결과가 외부에서 변경되지 않도록 5일 목록의 불변 복사본을 보관한다. */
        days = List.copyOf(days);
    }
}
