package com.module06.backend.meetingroom.presentation.api.response;

import com.module06.backend.meetingroom.application.result.MeetingRoomAvailabilitySummary;

/*
 * ROOM-02 주간 응답에서 조회 대상 회의실의 공통 표시 정보를 표현하는 프레젠테이션 DTO다.
 *
 * @param meetingRoomId 회의실 식별자
 * @param name 회의실 이름
 * @param availableFrom 이용 가능 시작 시각
 * @param availableTo 이용 가능 종료 시각
 */
public record MeetingRoomAvailabilityItemResponse(
        Long meetingRoomId,
        String name,
        String availableFrom,
        String availableTo
) {

    /*
     * 애플리케이션 회의실 현황 결과를 외부 API 응답 항목으로 변환한다.
     *
     * @param summary 변환할 회의실 현황 조회 결과
     * @return 회의실 식별자·이름·이용 가능 시간을 담은 응답 항목
     */
    public static MeetingRoomAvailabilityItemResponse from(MeetingRoomAvailabilitySummary summary) {
        /* 회의실 이용 가능 시각을 API 계약의 HH:mm 문자열로 변환한다. */
        return new MeetingRoomAvailabilityItemResponse(
                summary.meetingRoomId(),
                summary.name(),
                ApiTimeFormat.formatTime(summary.availableFrom()),
                ApiTimeFormat.formatTime(summary.availableTo())
        );
    }
}
