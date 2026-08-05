package com.module06.backend.meeting.domain.repository;

import java.util.List;

import com.module06.backend.meeting.domain.model.Meeting;

/*
 * 회의 예약 애그리거트를 원자적으로 저장하는 도메인 저장소 계약이다.
 *
 * 구현체는 meeting, meeting_room_slot, meeting_attendee 저장을 하나의 트랜잭션에서 조율한다.
 */
public interface MeetingRepository {

    /*
     * 회의와 점유 슬롯, 참석자 명단을 함께 저장한다.
     *
     * @param meeting 저장할 신규 회의
     * @return 데이터베이스 식별자와 생성 시각이 반영된 회의
     */
    Meeting saveReservation(Meeting meeting);

    /* 기존 명단과 목표 명단의 차이를 같은 트랜잭션에서 반영해 참석자를 전체 교체한다. */
    void replaceAttendees(Long meetingId, List<Long> attendeeMemberIds);
}
