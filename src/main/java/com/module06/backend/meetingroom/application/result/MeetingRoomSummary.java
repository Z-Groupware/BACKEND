package com.module06.backend.meetingroom.application.result;

import com.module06.backend.meetingroom.domain.model.MeetingRoom;

/*
 * ROOM-01 회의실 목록의 항목 하나를 표현하는 애플리케이션 결과 객체다.
 *
 * presentation 계층의 응답 DTO를 application 계층에서 직접 참조하지 않기 위한 경계이며,
 * API 응답에 필요한 회의실 기본 정보만 포함한다.
 *
 * @param meetingRoomId 회의실 식별자
 * @param name 회의실 이름
 * @param location 회의실 위치
 */
public record MeetingRoomSummary(
        Long meetingRoomId,
        String name,
        String location
) {

    /*
     * 회의실 도메인 객체를 ROOM-01 조회 결과로 변환한다.
     *
     * @param meetingRoom 변환할 회의실 도메인 객체
     * @return API 응답 계층으로 전달할 회의실 조회 결과
     */
    public static MeetingRoomSummary from(MeetingRoom meetingRoom) {
        /* 도메인 객체에서 목록 화면에 필요한 값만 선택해 결과 객체를 생성한다. */
        return new MeetingRoomSummary(
                meetingRoom.getId(),
                meetingRoom.getName(),
                meetingRoom.getLocation()
        );
    }
}
