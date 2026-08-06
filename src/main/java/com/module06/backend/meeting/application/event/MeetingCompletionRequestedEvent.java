package com.module06.backend.meeting.application.event;

import java.time.LocalDateTime;

/* D의 상태 저장이 커밋된 뒤 A가 조립 여부 확인과 분석을 시작할 내부 계약이다. */
public record MeetingCompletionRequestedEvent(
        Long companyId,
        Long meetingId,
        Long captureSessionId,
        LocalDateTime requestedAt
) {
}
