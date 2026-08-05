package com.module06.backend.cap.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import lombok.Getter;

/* comment.
    meeting_attendee 복합 PK(meeting_id, member_id). JPA @EmbeddedId 대상
    (project의 ProjectTeamId와 동일 패턴).
*/
@Getter
@Embeddable
public class MeetingAttendeeId implements Serializable {

    private Long meetingId;
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
