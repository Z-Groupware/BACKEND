package com.module06.backend.meeting.application.command;

import java.util.List;

/*
 * MEET-09 참석자 전체 교체에 필요한 인증 정보와 요청 명단을 묶은 Command다.
 *
 * 회사·요청자·권한 정보는 Access Token principal에서만 전달받는다.
 */
public record ReplaceMeetingAttendeesCommand(
        Long companyId,
        Long requesterMemberId,
        String requesterRole,
        boolean requesterAdmin,
        Long meetingId,
        List<Long> attendeeMemberIds
) {

    /* 외부 요청 목록이 Command 생성 이후 변경되지 않도록 불변 복사한다. */
    public ReplaceMeetingAttendeesCommand {
        /* null은 서비스 입력 검증이 처리하고 실제 목록만 방어적으로 복사한다. */
        if (attendeeMemberIds != null) {
            attendeeMemberIds = List.copyOf(attendeeMemberIds);
        }
    }
}
