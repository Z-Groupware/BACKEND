package com.module06.backend.meetingroom.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.meetingroom.domain.model.ReservedSlot;

/*
 * 예약된 30분 슬롯 조회를 추상화한 도메인 저장소 계약이다.
 *
 * 예약 현황은 회의 테이블을 시간 범위로 훑지 않고 물질화된 슬롯 행을 그대로 읽는다.
 * 슬롯 한 행이 곧 화면의 한 칸이므로 조회 결과를 다시 시간 계산으로 펼칠 필요가 없다.
 *
 * 연결된 클래스
 * - ReservedSlot: 이 계약이 반환하는 도메인 값 객체
 * - MeetingRoomAvailabilityService: ROOM-02 응답 조립에 이 계약만 사용한다
 */
public interface MeetingRoomSlotRepository {

    /*
     * 회의실 목록과 조회 기간에 걸린 예약 슬롯을 조회한다.
     * 구현체는 요청 회사에 속한 회의의 슬롯만 반환해 다른 회사의 예약 정보가 섞이지 않게 해야 한다.
     *
     * @param companyId 인증된 요청자의 회사 식별자
     * @param meetingRoomIds 조회 대상 회의실 식별자 목록
     * @param fromInclusive 조회 시작 일시, 이 시각을 포함한다
     * @param toExclusive 조회 종료 일시, 이 시각을 포함하지 않는다
     * @return 조건에 해당하는 예약 슬롯 목록, 없으면 빈 목록
     */
    List<ReservedSlot> findReservedSlots(
            Long companyId,
            List<Long> meetingRoomIds,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    );
}
