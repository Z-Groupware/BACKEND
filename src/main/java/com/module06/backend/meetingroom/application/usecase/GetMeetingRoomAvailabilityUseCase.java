package com.module06.backend.meetingroom.application.usecase;

import com.module06.backend.meetingroom.application.query.MeetingRoomAvailabilityQuery;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailability;

/*
 * ROOM-02 회의실 예약 현황 조회 기능의 인바운드 포트다.
 *
 * presentation 계층은 구현체가 아니라 이 계약에만 의존하며,
 * 인증 정보와 조회 조건을 하나의 조회 객체로 전달한다.
 */
public interface GetMeetingRoomAvailabilityUseCase {

    /*
     * 단일 회의실의 월요일부터 금요일까지 30분 슬롯 예약 현황을 조회한다.
     *
     * @param query 회사·구성원 식별자와 선택 기준일, 필수 회의실 식별자를 담은 조회 조건
     * @return 단일 회의실 주간 슬롯 현황 조회 결과
     */
    MeetingRoomAvailability getMeetingRoomAvailability(MeetingRoomAvailabilityQuery query);
}
