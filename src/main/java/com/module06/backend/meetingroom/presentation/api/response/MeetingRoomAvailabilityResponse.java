package com.module06.backend.meetingroom.presentation.api.response;

import java.util.List;

import com.module06.backend.meetingroom.application.result.MeetingRoomAvailability;

/*
 * ROOM-02 회의실 예약 현황 조회의 data 영역을 표현하는 프레젠테이션 DTO다.
 *
 * 단일 회의실의 월요일부터 금요일까지 시간표 전체를 이 응답 하나로 그릴 수 있게 한다.
 *
 * @param weekStart 조회 주의 월요일
 * @param weekEnd 조회 주의 금요일
 * @param slotMinutes 슬롯 하나의 길이(분)
 * @param meetingRoom 조회 대상 회의실 정보
 * @param days 월요일부터 금요일까지 날짜별 슬롯 현황
 */
public record MeetingRoomAvailabilityResponse(
        String weekStart,
        String weekEnd,
        int slotMinutes,
        MeetingRoomAvailabilityItemResponse meetingRoom,
        List<MeetingRoomDayResponse> days
) {

    /*
     * 응답 생성 이후 날짜별 목록이 변경되지 않도록 불변으로 복사한다.
     *
     * @param days 날짜별 슬롯 현황 응답 목록
     */
    public MeetingRoomAvailabilityResponse {
        /* 응답 컬렉션을 불변 복사해 계층 밖에서 주간 목록이 변경되는 것을 방지한다. */
        days = List.copyOf(days);
    }

    /*
     * 애플리케이션 조회 결과 전체를 ROOM-02 응답 data 객체로 변환한다.
     *
     * @param availability 변환할 회의실 예약 현황 조회 결과
     * @return 조회 날짜와 회의실별 슬롯 현황을 담은 data 객체
     */
    public static MeetingRoomAvailabilityResponse from(MeetingRoomAvailability availability) {
        /* 주간 범위와 회의실 메타, 날짜별 슬롯을 확정된 외부 JSON 구조로 변환한다. */
        return new MeetingRoomAvailabilityResponse(
                ApiTimeFormat.formatDate(availability.weekStart()),
                ApiTimeFormat.formatDate(availability.weekEnd()),
                availability.slotMinutes(),
                MeetingRoomAvailabilityItemResponse.from(availability.meetingRoom()),
                availability.days().stream()
                        .map(MeetingRoomDayResponse::from)
                        .toList()
        );
    }
}
