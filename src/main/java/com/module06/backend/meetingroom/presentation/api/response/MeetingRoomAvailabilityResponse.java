package com.module06.backend.meetingroom.presentation.api.response;

import java.util.List;

import com.module06.backend.meetingroom.application.result.MeetingRoomAvailability;

/*
 * ROOM-02 회의실 예약 현황 조회의 data 영역을 표현하는 프레젠테이션 DTO다.
 *
 * 화면의 시간표 그리드 전체가 이 응답 하나로 그려지므로 회의실 목록과 예약 상태를 나눠 호출하지 않는다.
 * 조회 결과가 0건이어도 404가 아니라 200과 빈 배열로 응답한다.
 *
 * @param date 조회한 날짜
 * @param slotMinutes 슬롯 하나의 길이(분)
 * @param meetingRooms 회의실별 슬롯 현황 목록
 */
public record MeetingRoomAvailabilityResponse(
        String date,
        int slotMinutes,
        List<MeetingRoomAvailabilityItemResponse> meetingRooms
) {

    /*
     * 응답 생성 이후 회의실 목록이 변경되지 않도록 불변으로 복사한다.
     *
     * @param meetingRooms 회의실 현황 응답 항목 목록
     */
    public MeetingRoomAvailabilityResponse {
        /* 응답 컬렉션을 불변 복사해 계층 밖에서 목록 내용이 변경되는 것을 방지한다. */
        meetingRooms = List.copyOf(meetingRooms);
    }

    /*
     * 애플리케이션 조회 결과 전체를 ROOM-02 응답 data 객체로 변환한다.
     *
     * @param availability 변환할 회의실 예약 현황 조회 결과
     * @return 조회 날짜와 회의실별 슬롯 현황을 담은 data 객체
     */
    public static MeetingRoomAvailabilityResponse from(MeetingRoomAvailability availability) {
        /* 날짜는 YYYY-MM-DD 문자열로 변환하고 회의실별 현황은 각 응답 항목으로 변환한다. */
        return new MeetingRoomAvailabilityResponse(
                ApiTimeFormat.formatDate(availability.date()),
                availability.slotMinutes(),
                availability.meetingRooms().stream()
                        .map(MeetingRoomAvailabilityItemResponse::from)
                        .toList()
        );
    }
}
