package com.module06.backend.meetingroom.domain.model;

/*
 * 회의실 예약 현황(ROOM-02)에서 30분 슬롯 하나의 예약 상태다.
 *
 * 프론트엔드는 이 값을 시간표 그리드의 클릭 가능 판정에 그대로 사용한다.
 * AVAILABLE 슬롯을 클릭하면 회의 개설 모달로 이어지므로, 예약 가능 여부 외의 의미를 담지 않는다.
 */
public enum MeetingRoomSlotStatus {

    /* 예약이 없어 회의를 개설할 수 있는 슬롯이다. */
    AVAILABLE,

    /* 이미 회의가 점유한 슬롯이다. */
    RESERVED
}
