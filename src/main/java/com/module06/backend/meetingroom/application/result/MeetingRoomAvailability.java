package com.module06.backend.meetingroom.application.result;

import java.time.LocalDate;
import java.util.List;

/*
 * ROOM-02 회의실 예약 현황 조회 결과 전체를 표현하는 애플리케이션 결과 객체다.
 *
 * 화면의 시간표 그리드 전체가 이 결과 하나로 그려지므로
 * 회의실 목록과 예약 상태를 별도 응답으로 나누지 않는다.
 *
 * @param date 조회한 날짜
 * @param slotMinutes 슬롯 하나의 길이(분)
 * @param meetingRooms 회의실별 슬롯 현황 목록
 */
public record MeetingRoomAvailability(
        LocalDate date,
        int slotMinutes,
        List<MeetingRoomAvailabilitySummary> meetingRooms
) {

    /*
     * 회의실 목록을 불변으로 복사해 결과 생성 이후 변경되지 않도록 보호한다.
     *
     * @param meetingRooms 회의실별 슬롯 현황 목록
     */
    public MeetingRoomAvailability {
        /* 조회 결과가 0건이어도 null이 아닌 빈 목록을 유지해 응답이 항상 배열로 직렬화되게 한다. */
        meetingRooms = List.copyOf(meetingRooms);
    }
}
