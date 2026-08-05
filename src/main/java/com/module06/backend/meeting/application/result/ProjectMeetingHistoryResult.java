package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * E 인수인계의 프로젝트 맥락 타임라인에 제공할 회의 한 건의 D도메인 내부 결과다.
 */
public record ProjectMeetingHistoryResult(
        Long meetingId,
        String title,
        LocalDateTime startAt,
        Long hostMemberId,
        MeetingStatus status
) {
}
