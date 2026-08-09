package com.module06.backend.meetingroom.application.result;

import java.time.LocalTime;

import com.module06.backend.meetingroom.domain.model.MeetingRoom;

/*
 * ROOM-04 수정 완료 후 프레젠테이션 계층에 전달할 회의실 전체 상태다.
 */
public record MeetingRoomUpdateResult(
        Long meetingRoomId,
        String name,
        String location,
        int capacity,
        LocalTime availableFrom,
        LocalTime availableTo
) {

    /* 저장된 회의실 도메인을 수정 결과 읽기 모델로 변환한다. */
    public static MeetingRoomUpdateResult from(MeetingRoom meetingRoom) {
        /* API 응답에 필요한 현재 속성만 복사하고 비활성화 내부 값은 노출하지 않는다. */
        return new MeetingRoomUpdateResult(
                meetingRoom.getId(),
                meetingRoom.getName(),
                meetingRoom.getLocation(),
                meetingRoom.getCapacity(),
                meetingRoom.getAvailableFrom(),
                meetingRoom.getAvailableTo()
        );
    }
}
