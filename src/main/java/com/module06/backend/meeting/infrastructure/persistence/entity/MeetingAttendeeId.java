package com.module06.backend.meeting.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

/*
 * meeting_attendee의 복합 PK(meeting_id, member_id)를 표현한다.
 */
public class MeetingAttendeeId implements Serializable {

    /* 참석 대상 회의 식별자다. */
    private Long meetingId;

    /* 참석 구성원 식별자다. */
    private Long memberId;

    /* JPA가 복합 식별자를 생성할 수 있도록 기본 생성자를 제공한다. */
    protected MeetingAttendeeId() {
    }

    /* 두 식별자로 복합 PK 값을 만든다. */
    public MeetingAttendeeId(Long meetingId, Long memberId) {
        /* 복합 키의 두 값을 그대로 저장한다. */
        this.meetingId = meetingId;
        this.memberId = memberId;
    }

    /* 두 복합 키 값이 모두 같은지 비교한다. */
    @Override
    public boolean equals(Object other) {
        /* 동일 인스턴스는 즉시 같은 값으로 판단한다. */
        if (this == other) {
            return true;
        }
        /* 같은 식별자 타입이 아니면 다른 값이다. */
        if (!(other instanceof MeetingAttendeeId that)) {
            return false;
        }
        /* 회의와 구성원 식별자가 모두 같아야 같은 참석자 행이다. */
        return Objects.equals(meetingId, that.meetingId) && Objects.equals(memberId, that.memberId);
    }

    /* equals와 동일한 값으로 해시 코드를 계산한다. */
    @Override
    public int hashCode() {
        /* 해시 컬렉션에서도 복합 키 동등성 규칙을 유지한다. */
        return Objects.hash(meetingId, memberId);
    }
}
