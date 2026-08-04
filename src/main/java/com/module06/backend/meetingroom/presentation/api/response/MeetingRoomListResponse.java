package com.module06.backend.meetingroom.presentation.api.response;

import java.util.List;

import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;

/*
 * ROOM-01 회의실 목록 조회의 data 영역을 표현하는 프레젠테이션 DTO다.
 *
 * 목록이 없을 때도 null 대신 빈 배열이 직렬화되도록 항상 List 값을 보관한다.
 *
 * @param meetingRooms 조회된 회의실 응답 항목 목록
 */
public record MeetingRoomListResponse(List<MeetingRoomItemResponse> meetingRooms) {

    /*
     * 외부에서 전달된 목록을 복사해 응답 생성 이후 변경되지 않도록 보호한다.
     *
     * @param meetingRooms 조회된 회의실 응답 항목 목록
     */
    public MeetingRoomListResponse {
        /* 응답 컬렉션을 불변 복사해 계층 밖에서 목록 내용이 변경되는 것을 방지한다. */
        meetingRooms = List.copyOf(meetingRooms);
    }

    /*
     * 애플리케이션 결과 목록 전체를 ROOM-01 응답 data 객체로 변환한다.
     *
     * @param summaries 변환할 회의실 조회 결과 목록
     * @return 변환된 회의실 응답 목록을 포함한 data 객체
     */
    public static MeetingRoomListResponse from(List<MeetingRoomSummary> summaries) {
        /* 각 조회 결과를 프레젠테이션 항목으로 변환하고 불변 응답 객체를 생성한다. */
        return new MeetingRoomListResponse(
                summaries.stream()
                        .map(MeetingRoomItemResponse::from)
                        .toList()
        );
    }
}
