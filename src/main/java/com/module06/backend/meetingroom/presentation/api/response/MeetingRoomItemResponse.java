package com.module06.backend.meetingroom.presentation.api.response;

import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;

/*
 * ROOM-01 응답의 회의실 항목 하나를 표현하는 프레젠테이션 DTO다.
 *
 * 위치가 등록되지 않은 회의실은 location을 null로 반환한다.
 *
 * @param meetingRoomId 회의실 식별자
 * @param name 회의실 이름
 * @param location 회의실 위치
 */
public record MeetingRoomItemResponse(
        Long meetingRoomId,
        String name,
        String location
) {

    /*
     * 애플리케이션 조회 결과를 외부 API 응답 항목으로 변환한다.
     *
     * @param summary 변환할 회의실 조회 결과
     * @return 회의실 식별자·이름·위치를 포함한 응답 항목
     */
    public static MeetingRoomItemResponse from(MeetingRoomSummary summary) {
        /* 24시간 운영은 공통 정책이므로 회의실별 시간 필드를 내려주지 않는다. */
        return new MeetingRoomItemResponse(
                summary.meetingRoomId(),
                summary.name(),
                summary.location()
        );
    }
}
