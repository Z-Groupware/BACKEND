package com.module06.backend.meeting.infrastructure.persistence.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/*
 * meeting_room_slot의 복합 PK(meeting_room_id, slot_start)를 표현한다.
 */
public class MeetingReservationSlotId implements Serializable {

    /* 슬롯이 속한 회의실 식별자다. */
    private Long meetingRoomId;

    /* 30분 슬롯의 시작 일시다. */
    private LocalDateTime slotStart;

    /* JPA가 복합 식별자를 생성할 수 있도록 기본 생성자를 제공한다. */
    protected MeetingReservationSlotId() {
    }

    /* 회의실과 슬롯 시작 시각으로 복합 PK 값을 만든다. */
    public MeetingReservationSlotId(Long meetingRoomId, LocalDateTime slotStart) {
        /* 복합 키의 두 값을 그대로 저장한다. */
        this.meetingRoomId = meetingRoomId;
        this.slotStart = slotStart;
    }

    /* 두 복합 키 값이 모두 같은지 비교한다. */
    @Override
    public boolean equals(Object other) {
        /* 동일 인스턴스는 즉시 같은 값으로 판단한다. */
        if (this == other) {
            return true;
        }
        /* 같은 식별자 타입이 아니면 다른 값이다. */
        if (!(other instanceof MeetingReservationSlotId that)) {
            return false;
        }
        /* 회의실과 시작 시각이 모두 같아야 같은 슬롯이다. */
        return Objects.equals(meetingRoomId, that.meetingRoomId) && Objects.equals(slotStart, that.slotStart);
    }

    /* equals와 동일한 값으로 해시 코드를 계산한다. */
    @Override
    public int hashCode() {
        /* 해시 컬렉션에서도 복합 키 동등성 규칙을 유지한다. */
        return Objects.hash(meetingRoomId, slotStart);
    }
}
