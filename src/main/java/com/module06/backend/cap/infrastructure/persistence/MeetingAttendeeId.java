package com.module06.backend.cap.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

/* comment.
    meeting_attendee 복합 PK(meeting_id, member_id). JPA @EmbeddedId 대상
    (project의 ProjectTeamId와 동일 패턴).

    ⚠️ @Column(name=...)을 명시하는 이유: meetingroom 도메인도 같은 meeting_attendee 테이블을
    read-model로 매핑한다. @Column을 생략하면 논리 컬럼명이 camelCase(meetingId)로 잡혀서,
    meetingroom이 쓰는 논리명(meeting_id)과 같은 물리 컬럼을 서로 다른 논리명으로 가리키게 되고
    Hibernate가 DuplicateMappingException으로 컨텍스트를 죽인다. 물리 컬럼명으로 고정해 정렬한다.
*/
@Getter
@Embeddable
public class MeetingAttendeeId implements Serializable {

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "member_id")
    private Long memberId;

    protected MeetingAttendeeId() {
    }

    public MeetingAttendeeId(Long meetingId, Long memberId) {
        this.meetingId = meetingId;
        this.memberId = memberId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MeetingAttendeeId that)) return false;
        return Objects.equals(meetingId, that.meetingId) && Objects.equals(memberId, that.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meetingId, memberId);
    }
}
