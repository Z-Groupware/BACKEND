package com.module06.backend.meeting.application.command;

import java.time.LocalDateTime;
import java.util.List;

/*
 * MEET-01 회의 개설 유스케이스의 입력값이다.
 *
 * companyId·hostMemberId·hostTeamId·hostRole은 요청 본문이 아니라 인증 principal에서만 채워진다.
 */
public record CreateMeetingCommand(
        Long companyId,
        Long hostMemberId,
        Long hostTeamId,
        String hostRole,
        String title,
        Long projectId,
        Long meetingRoomId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean recordingConsent,
        Long relatedActionId,
        List<Long> attendeeMemberIds,
        String mainTopic,
        List<String> subTopics
) {

    /* 외부에서 참석자와 소주제 목록을 바꾸지 못하도록 생성 시점에 불변 복사한다. */
    public CreateMeetingCommand {
        attendeeMemberIds = attendeeMemberIds == null ? null : List.copyOf(attendeeMemberIds);
        subTopics = subTopics == null ? null : List.copyOf(subTopics);
    }
}
