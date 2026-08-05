package com.module06.backend.meeting.domain.model;

/*
 * 회의의 생명주기 상태다.
 *
 * MEET-01은 예약 직후 SCHEDULED 상태를 만들며, 이후 입장과 종료 API가
 * IN_PROGRESS와 DONE으로 순서대로 전이한다.
 */
public enum MeetingStatus {

    /* 아직 시작되지 않은 예약 회의다. */
    SCHEDULED,

    /* 한 명 이상의 참석자가 입장해 진행 중인 회의다. */
    IN_PROGRESS,

    /* 종료 처리가 완료된 회의다. */
    DONE
}
