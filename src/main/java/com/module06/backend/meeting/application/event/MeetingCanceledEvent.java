package com.module06.backend.meeting.application.event;

import java.time.LocalDateTime;
import java.util.List;

/* 회의 취소 트랜잭션이 커밋된 뒤 알림 도메인이 소비할 D 소유 내부 이벤트다. */
public record MeetingCanceledEvent(
        Long meetingId,
        Long companyId,
        Long hostMemberId,
        List<Long> attendeeMemberIds,
        String title,
        LocalDateTime startAt,
        LocalDateTime canceledAt
) {

    /* 발행 이후 참석자 원본이 변경되지 않도록 이벤트 명단을 불변 목록으로 복사한다. */
    public MeetingCanceledEvent {
        /* 저장된 회의의 참석자 목록은 필수이므로 누락을 숨기지 않고 즉시 실패하게 한다. */
        attendeeMemberIds = List.copyOf(attendeeMemberIds);
    }
}
