package com.module06.backend.meeting.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * MEET-01이 점유하는 30분 예약 슬롯 한 칸을 meeting_room_slot 테이블에 매핑한다.
 *
 * (meeting_room_id, slot_start) 복합 PK가 동시에 들어온 중복 예약 중 하나만 허용한다.
 */
@Entity
@Table(name = "meeting_room_slot")
@IdClass(MeetingReservationSlotId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingReservationSlotJpaEntity {

    /* 복합 PK 첫 번째 값인 회의실 식별자다. */
    @Id
    @Column(name = "meeting_room_id", nullable = false)
    private Long meetingRoomId;

    /* 복합 PK 두 번째 값인 슬롯 시작 일시다. */
    @Id
    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    /* 이 슬롯을 점유한 회의 식별자다. */
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* 회의실과 슬롯 시작 시각, 점유 회의로 예약 슬롯 행을 생성한다. */
    public MeetingReservationSlotJpaEntity(Long meetingRoomId, LocalDateTime slotStart, Long meetingId) {
        /* 복합 키와 점유 회의 식별자를 각 컬럼에 대응시킨다. */
        this.meetingRoomId = meetingRoomId;
        this.slotStart = slotStart;
        this.meetingId = meetingId;
    }
}
