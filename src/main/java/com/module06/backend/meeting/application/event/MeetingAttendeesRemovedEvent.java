package com.module06.backend.meeting.application.event;

import java.time.LocalDateTime;
import java.util.List;

/*
 * MEET-09 교체로 명단에서 빠진 참석자에게 제외 알림을 보내기 위한 내부 이벤트다.
 *
 * 알림 도메인은 AFTER_COMMIT 리스너로 소비해 롤백된 명단 변경의 알림을 발송하지 않아야 한다.
 */
public record MeetingAttendeesRemovedEvent(
        Long meetingId,
        Long companyId,
        Long hostMemberId,
        List<Long> removedAttendeeMemberIds,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt
) {

    /* 제외된 참석자 목록이 이벤트 발행 이후 변경되지 않도록 불변 복사한다. */
    public MeetingAttendeesRemovedEvent {
        /* 이벤트는 실제 제외된 구성원 목록을 항상 제공하므로 불변 목록으로 보관한다. */
        removedAttendeeMemberIds = List.copyOf(removedAttendeeMemberIds);
    }
}
