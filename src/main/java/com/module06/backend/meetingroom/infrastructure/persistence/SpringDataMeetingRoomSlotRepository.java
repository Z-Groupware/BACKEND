package com.module06.backend.meetingroom.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/*
 * meeting_room_slot 테이블 조회를 수행하는 Spring Data JPA 저장소다.
 *
 * infrastructure 계층 내부에서만 기술 저장소로 사용하며,
 * application 계층은 MeetingRoomSlotRepository 도메인 계약을 통해서만 데이터에 접근한다.
 */
public interface SpringDataMeetingRoomSlotRepository extends JpaRepository<MeetingRoomSlotJpaEntity, MeetingRoomSlotId> {

    /*
     * 여러 회의실의 예약 슬롯을 시각 범위로 조회한다.
     * 종료 일시를 미포함으로 두어 하루 경계의 슬롯이 두 날짜에 중복으로 조회되지 않게 한다.
     * 복합 PK 선두 컬럼이 회의실 식별자라 이 조건 조합은 인덱스를 그대로 탄다.
     *
     * @param meetingRoomIds 조회할 회의실 식별자 목록
     * @param fromInclusive 조회 시작 일시, 이 시각을 포함한다
     * @param toExclusive 조회 종료 일시, 이 시각을 포함하지 않는다
     * @return 조건에 해당하는 슬롯 영속성 엔티티 목록
     */
    @Query("""
            SELECT slot
            FROM MeetingRoomSlotJpaEntity slot
            WHERE slot.meetingRoomId IN :meetingRoomIds
              AND slot.slotStart >= :fromInclusive
              AND slot.slotStart < :toExclusive
            ORDER BY slot.meetingRoomId ASC, slot.slotStart ASC
            """)
    List<MeetingRoomSlotJpaEntity> findReservedSlots(
            @Param("meetingRoomIds") List<Long> meetingRoomIds,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );
}
