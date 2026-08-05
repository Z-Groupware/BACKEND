package com.module06.backend.meetingroom.infrastructure.persistence;

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
 * meeting_room_slot 테이블을 매핑하는 JPA 영속성 엔티티다.
 *
 * 회의가 점유하는 30분 슬롯 한 칸이 한 행이며, (meeting_room_id, slot_start) 복합 PK가
 * 같은 회의실·같은 시각의 두 번째 예약을 데이터베이스 수준에서 거부한다.
 * 회의실·회의와 JPA 연관관계를 만들지 않고 식별자 값만 보관해 애그리거트 사이의 결합을 만들지 않는다.
 * created_at은 데이터베이스 기본값이 채우므로 매핑하지 않는다.
 *
 * 연결된 클래스
 * - MeetingRoomSlotId: 복합 PK 식별자 클래스
 * - SpringDataMeetingRoomSlotRepository: 슬롯 범위 조회를 수행하는 기술 저장소
 */
@Entity
@Table(name = "meeting_room_slot")
@IdClass(MeetingRoomSlotId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingRoomSlotJpaEntity {

    /* 슬롯을 점유한 회의실 식별자이며 복합 PK의 첫 번째 컬럼이다. */
    @Id
    @Column(name = "meeting_room_id", nullable = false)
    private Long meetingRoomId;

    /* 30분 그리드의 슬롯 시작 시각이며 복합 PK의 두 번째 컬럼이다. */
    @Id
    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    /* 이 슬롯을 점유한 회의의 식별자다. */
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /*
     * 슬롯 행을 생성한다.
     * 예약 현황 조회는 읽기만 하지만, 테스트 데이터 준비와 이후 회의 개설의 슬롯 등록에서 사용한다.
     *
     * @param meetingRoomId 슬롯을 점유한 회의실 식별자
     * @param slotStart 슬롯 시작 시각
     * @param meetingId 슬롯을 점유한 회의 식별자
     */
    public MeetingRoomSlotJpaEntity(Long meetingRoomId, LocalDateTime slotStart, Long meetingId) {
        /* 복합 PK 구성 값과 점유 회의 식별자를 각 컬럼에 대응하는 필드에 저장한다. */
        this.meetingRoomId = meetingRoomId;
        this.slotStart = slotStart;
        this.meetingId = meetingId;
    }
}
