package com.module06.backend.meeting.application.event;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 회의 정보 수정 트랜잭션 안에서 발행하는 D 소유 내부 이벤트다.
 *
 * 알림 소비자는 AFTER_COMMIT으로 구독해 롤백된 슬롯 변경 알림을 보내지 않아야 한다.
 */
public record MeetingUpdatedEvent(
        Long meetingId,
        Long companyId,
        Long hostMemberId,
        List<Long> attendeeMemberIds,
        Long projectId,
        Long meetingRoomId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean recordingConsent
) {

    /* 발행 이후 참석자 원본이 변경되지 않도록 불변 목록으로 복사한다. */
    public MeetingUpdatedEvent {
        /* 정상 회의의 참석자 목록은 null이 아니므로 누락을 숨기지 않고 즉시 실패하게 한다. */
        attendeeMemberIds = List.copyOf(attendeeMemberIds);
    }
}
