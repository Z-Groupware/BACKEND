package com.module06.backend.meeting.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * 회의 입장 허용 명단의 단일 행을 meeting_attendee 테이블에 매핑한다.
 *
 * 회의와 구성원 엔티티 연관관계 대신 두 식별자만 보관한다.
 */
@Entity
@Table(name = "meeting_attendee")
@IdClass(MeetingAttendeeId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingAttendeeJpaEntity {

    /* 복합 PK 첫 번째 값인 회의 식별자다. */
    @Id
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* 복합 PK 두 번째 값인 구성원 식별자다. */
    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /* 회의와 구성원 식별자로 참석자 행을 생성한다. */
    public MeetingAttendeeJpaEntity(Long meetingId, Long memberId) {
        /* 다른 도메인의 엔티티를 참조하지 않고 식별자만 저장한다. */
        this.meetingId = meetingId;
        this.memberId = memberId;
    }
}
