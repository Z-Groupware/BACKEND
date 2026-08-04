package com.module06.backend.meetingroom.application.usecase;

import java.util.List;

import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;

/*
 * ROOM-01 회의실 목록 조회 기능의 인바운드 포트다.
 *
 * presentation 계층은 구현체가 아니라 이 계약에만 의존하며,
 * 인증 정보에서 얻은 회사 식별자를 전달해 회사별 회의실 목록을 조회한다.
 */
public interface GetMeetingRoomListUseCase {

    /*
     * 요청자가 소속된 회사에서 사용할 수 있는 회의실 목록을 조회한다.
     *
     * @param companyId 인증된 요청자의 회사 식별자
     * @return 활성 회의실 요약 목록, 조회 결과가 없으면 빈 목록
     */
    List<MeetingRoomSummary> getMeetingRooms(Long companyId);
}
