package com.module06.backend.meetingroom.presentation.api.response;

import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;

/*
 * ROOM-01 응답의 회의실 항목 하나를 표현하는 프레젠테이션 DTO다.
 *
 * 이용 가능 시각은 프론트엔드 계약에 맞춰 HH:mm 문자열로 변환하며,
 * 위치가 등록되지 않은 회의실은 location을 null로 반환한다.
 *
 * @param meetingRoomId 회의실 식별자
 * @param name 회의실 이름
 * @param location 회의실 위치
 * @param availableFrom 이용 가능 시작 시각
 * @param availableTo 이용 가능 종료 시각
 */
public record MeetingRoomItemResponse(
        Long meetingRoomId,
        String name,
        String location,
        String availableFrom,
        String availableTo
) {

    /*
     * 애플리케이션 조회 결과를 외부 API 응답 항목으로 변환한다.
     *
     * @param summary 변환할 회의실 조회 결과
     * @return HH:mm 형식의 이용 가능 시각을 포함한 응답 항목
     */
    public static MeetingRoomItemResponse from(MeetingRoomSummary summary) {
        /* 도메인 시간 타입을 API 계약의 문자열 표현으로 변환해 응답을 생성한다. */
        return new MeetingRoomItemResponse(
                summary.meetingRoomId(),
                summary.name(),
                summary.location(),
                ApiTimeFormat.formatTime(summary.availableFrom()),
                ApiTimeFormat.formatTime(summary.availableTo())
        );
    }
}
