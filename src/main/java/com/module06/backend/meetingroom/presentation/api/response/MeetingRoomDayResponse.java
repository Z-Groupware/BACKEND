package com.module06.backend.meetingroom.presentation.api.response;

import java.util.List;

import com.module06.backend.meetingroom.application.result.MeetingRoomDayAvailability;

/*
 * ROOM-02 주간 응답에서 평일 하루의 날짜·요일·슬롯 목록을 표현하는 프레젠테이션 DTO다.
 *
 * @param date 슬롯이 속한 날짜 문자열
 * @param dayOfWeek MON부터 FRI까지의 요일 이름
 * @param slots 해당 날짜의 30분 슬롯 현황
 */
public record MeetingRoomDayResponse(
        String date,
        String dayOfWeek,
        List<MeetingRoomSlotResponse> slots
) {

    /* 날짜별 슬롯 응답 목록을 불변으로 복사해 직렬화 도중 변경되지 않게 한다. */
    public MeetingRoomDayResponse {
        /* 예약이 없는 날에도 null이 아닌 전체 AVAILABLE 슬롯 목록을 유지한다. */
        slots = List.copyOf(slots);
    }

    /* 애플리케이션 날짜별 결과를 외부 JSON 계약으로 변환한다. */
    public static MeetingRoomDayResponse from(MeetingRoomDayAvailability availability) {
        /* 날짜는 ISO 문자열, 요일은 MON~FRI 축약형, 슬롯 시각은 각 응답 DTO의 HH:mm 형식으로 변환한다. */
        return new MeetingRoomDayResponse(
                ApiTimeFormat.formatDate(availability.date()),
                availability.dayOfWeek().name().substring(0, 3),
                availability.slots().stream()
                        .map(MeetingRoomSlotResponse::from)
                        .toList()
        );
    }
}
