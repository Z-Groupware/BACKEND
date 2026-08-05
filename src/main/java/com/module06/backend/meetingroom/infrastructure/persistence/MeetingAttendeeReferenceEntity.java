package com.module06.backend.meetingroom.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * meeting_attendee 테이블을 읽기 전용으로 조회하는 참석자 참조 엔티티다.
 *
 * 참석자 명단의 주인은 회의 기능이며, 회의실 현황 조회는 "요청자가 이 회의의 참석자인가"만 알면 된다.
 * 그래서 복합 PK 두 컬럼만 매핑하고 @Immutable로 쓰기를 막는다.
 *
 * 연결된 클래스
 * - MeetingAttendeeReferenceId: 복합 PK 식별자 클래스
 * - MeetingAttendancePersistenceAdapter: 참석 여부 조회 도메인 계약 구현체
 */
@Entity
@Table(name = "meeting_attendee")
@IdClass(MeetingAttendeeReferenceId.class)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingAttendeeReferenceEntity {

    /* 참석 대상 회의의 식별자이며 복합 PK의 첫 번째 컬럼이다. */
    @Id
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* 참석자로 등록된 구성원의 식별자이며 복합 PK의 두 번째 컬럼이다. */
    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /*
     * 테스트에서 참석자 데이터를 준비할 수 있게 한다.
     * 운영 경로에서 이 엔티티로 참석자를 저장하지 않으며, 명단 관리는 회의 기능이 담당한다.
     *
     * @param meetingId 참석 대상 회의 식별자
     * @param memberId 참석자로 등록된 구성원 식별자
     */
    public MeetingAttendeeReferenceEntity(Long meetingId, Long memberId) {
        /* 참석 여부 판단에 필요한 두 식별자만 저장한다. */
        this.meetingId = meetingId;
        this.memberId = memberId;
    }
}
