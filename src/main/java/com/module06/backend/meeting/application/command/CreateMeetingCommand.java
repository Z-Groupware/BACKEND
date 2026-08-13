package com.module06.backend.meeting.application.command;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
        /* null 원소의 계약 판정은 서비스가 MT-010으로 처리하므로 값은 보존한 채 변경만 막는다. */
        attendeeMemberIds = attendeeMemberIds == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(attendeeMemberIds));

        /* 소주제는 null 원소를 허용하지 않으며 프레젠테이션·도메인 검증 전에 방어적으로 복사한다. */
        subTopics = subTopics == null ? null : List.copyOf(subTopics);
    }
}
