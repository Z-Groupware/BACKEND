package com.module06.backend.meeting.application.event;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 회의 예약 트랜잭션 안에서 발행하는 내부 이벤트다.
 *
 * 알림 도메인은 AFTER_COMMIT 리스너로 이 이벤트를 소비해 롤백된 예약의 알림이 전송되지 않게 해야 한다.
 */
public record MeetingReservedEvent(
        Long meetingId,
        Long companyId,
        Long hostMemberId,
        List<Long> attendeeMemberIds,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean recordingConsent
) {

    /* 이벤트의 참석자 목록이 발행 이후 변경되지 않도록 불변 복사한다. */
    public MeetingReservedEvent {
        attendeeMemberIds = List.copyOf(attendeeMemberIds);
    }
}
