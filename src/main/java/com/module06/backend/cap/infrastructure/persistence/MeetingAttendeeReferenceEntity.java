package com.module06.backend.cap.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    D(회의) 소유 meeting_attendee 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    "이 회의의 참석자인가"만 존재 여부로 확인하는 용도라 복합키 외 컬럼은 없다.
*/
@Entity
@Table(name = "meeting_attendee")
@Immutable
@Getter
@NoArgsConstructor
public class MeetingAttendeeReferenceEntity {

    @EmbeddedId
    private MeetingAttendeeId id;
}
