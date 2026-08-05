package com.module06.backend.meetingroom.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

/*
 * meeting_attendee 테이블의 복합 PK(meeting_id, member_id)를 표현하는 식별자 클래스다.
 *
 * 참석 여부 판단에만 쓰는 읽기 전용 참조이므로 식별자 두 값 외의 속성은 담지 않는다.
 *
 * 연결된 클래스
 * - MeetingAttendeeReferenceEntity: 이 클래스를 @IdClass로 사용하는 참조 엔티티
 */
public class MeetingAttendeeReferenceId implements Serializable {

    /* 참석 대상 회의의 식별자다. */
    private Long meetingId;

    /* 참석자로 등록된 구성원의 식별자다. */
    private Long memberId;

    /*
     * JPA가 식별자 인스턴스를 생성할 수 있도록 기본 생성자를 제공한다.
     */
    protected MeetingAttendeeReferenceId() {
    }

    /*
     * 회의 식별자와 구성원 식별자로 복합 식별자를 생성한다.
     *
     * @param meetingId 참석 대상 회의 식별자
     * @param memberId 참석자로 등록된 구성원 식별자
     */
    public MeetingAttendeeReferenceId(Long meetingId, Long memberId) {
        /* 복합 PK를 구성하는 두 값을 그대로 보관한다. */
        this.meetingId = meetingId;
        this.memberId = memberId;
    }

    /*
     * 복합 식별자의 동등성을 두 구성 값으로 판단한다.
     *
     * @param other 비교 대상 객체
     * @return 회의 식별자와 구성원 식별자가 모두 같으면 true
     */
    @Override
    public boolean equals(Object other) {
        /* JPA가 식별자 비교로 엔티티 동일성을 판단하므로 값 기반 비교를 제공한다. */
        if (this == other) {
            return true;
        }
        if (!(other instanceof MeetingAttendeeReferenceId that)) {
            return false;
        }
        return Objects.equals(meetingId, that.meetingId) && Objects.equals(memberId, that.memberId);
    }

    /*
     * equals와 동일한 두 구성 값으로 해시 코드를 계산한다.
     *
     * @return 복합 식별자의 해시 코드
     */
    @Override
    public int hashCode() {
        /* equals와 같은 필드를 사용해 해시 기반 컬렉션에서도 일관되게 동작하게 한다. */
        return Objects.hash(meetingId, memberId);
    }
}
