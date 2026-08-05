package com.module06.backend.meetingroom.domain.repository;

import java.util.List;
import java.util.Set;

/*
 * 요청자의 회의 참석 여부 조회를 추상화한 도메인 저장소 계약이다.
 *
 * 예약 현황판은 회사 전체가 공유하지만 회의 제목은 참석자에게만 노출한다.
 * 그 판단에 필요한 최소 정보만 식별자 집합으로 받아오며, 회의 애그리거트를 끌어오지 않는다.
 * 회의 도메인 구현이 들어오면 어댑터만 그 도메인 포트에 연결하고 이 계약과 서비스는 그대로 둔다.
 *
 * 연결된 클래스
 * - MeetingRoomAvailabilityService: 조회한 참석 회의 식별자로 제목 마스킹을 결정한다
 * - ReservedSlot: 참석 여부를 받아 노출할 제목을 결정한다
 */
public interface MeetingAttendanceRepository {

    /*
     * 주어진 회의 중 요청자가 참석자로 등록된 회의의 식별자를 조회한다.
     *
     * @param memberId 인증된 요청자의 구성원 식별자
     * @param meetingIds 참석 여부를 확인할 회의 식별자 목록
     * @return 요청자가 참석자인 회의 식별자 집합, 없으면 빈 집합
     */
    Set<Long> findAttendedMeetingIds(Long memberId, List<Long> meetingIds);
}
