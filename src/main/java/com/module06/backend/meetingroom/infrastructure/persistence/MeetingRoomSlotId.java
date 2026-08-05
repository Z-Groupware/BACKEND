package com.module06.backend.meetingroom.infrastructure.persistence;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/*
 * meeting_room_slot 테이블의 복합 PK(meeting_room_id, slot_start)를 표현하는 식별자 클래스다.
 *
 * 이 복합 PK가 예약 중복 방지의 핵심이다. "같은 회의실의 겹치는 시간"을 30분 슬롯의 등호 판정으로 축소해
 * 데이터베이스가 물리적으로 중복 예약을 거부하게 만든다(MySQL 8에는 범위 배제 제약이 없다).
 * @EmbeddedId가 아니라 @IdClass를 쓰는 이유는 엔티티에서 회의실 식별자와 슬롯 시각을 평면 필드로 두어
 * 슬롯 범위 조회 쿼리를 중첩 경로 없이 작성하기 위한 것이다.
 *
 * 연결된 클래스
 * - MeetingRoomSlotJpaEntity: 이 클래스를 @IdClass로 사용하는 영속성 엔티티
 */
public class MeetingRoomSlotId implements Serializable {

    /* 슬롯을 점유한 회의실 식별자다. */
    private Long meetingRoomId;

    /* 30분 그리드의 슬롯 시작 시각이다. */
    private LocalDateTime slotStart;

    /*
     * JPA가 식별자 인스턴스를 생성할 수 있도록 기본 생성자를 제공한다.
     */
    protected MeetingRoomSlotId() {
    }

    /*
     * 회의실 식별자와 슬롯 시작 시각으로 복합 식별자를 생성한다.
     *
     * @param meetingRoomId 슬롯을 점유한 회의실 식별자
     * @param slotStart 슬롯 시작 시각
     */
    public MeetingRoomSlotId(Long meetingRoomId, LocalDateTime slotStart) {
        /* 복합 PK를 구성하는 두 값을 그대로 보관한다. */
        this.meetingRoomId = meetingRoomId;
        this.slotStart = slotStart;
    }

    /*
     * 복합 식별자의 동등성을 두 구성 값으로 판단한다.
     *
     * @param other 비교 대상 객체
     * @return 회의실 식별자와 슬롯 시작 시각이 모두 같으면 true
     */
    @Override
    public boolean equals(Object other) {
        /* JPA가 식별자 비교로 엔티티 동일성을 판단하므로 값 기반 비교를 제공한다. */
        if (this == other) {
            return true;
        }
        if (!(other instanceof MeetingRoomSlotId that)) {
            return false;
        }
        return Objects.equals(meetingRoomId, that.meetingRoomId) && Objects.equals(slotStart, that.slotStart);
    }

    /*
     * equals와 동일한 두 구성 값으로 해시 코드를 계산한다.
     *
     * @return 복합 식별자의 해시 코드
     */
    @Override
    public int hashCode() {
        /* equals와 같은 필드를 사용해 해시 기반 컬렉션에서도 일관되게 동작하게 한다. */
        return Objects.hash(meetingRoomId, slotStart);
    }
}
