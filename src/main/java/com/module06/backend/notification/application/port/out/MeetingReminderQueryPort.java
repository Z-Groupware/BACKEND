package com.module06.backend.notification.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 알림 도메인이 회의 저장 구조를 알지 않고 10분 전 알림 대상만 조회하기 위한 아웃바운드 포트다.
 */
public interface MeetingReminderQueryPort {

    /* 예약 시작 시각이 반개구간에 포함되는 회의를 회사 구분과 함께 일괄 조회한다. */
    List<MeetingReminderTarget> findScheduledMeetingsStartingBetween(
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    );

    /* SSE 표시와 알림 이력 저장에 필요한 회의 최소 읽기 모델이다. */
    record MeetingReminderTarget(
            Long companyId,
            Long meetingId,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long meetingRoomId,
            String meetingRoomName,
            List<Long> attendeeMemberIds
    ) {
        /* 어댑터가 반환한 최종 참석자 명단을 이후 단계에서 변경할 수 없게 고정한다. */
        public MeetingReminderTarget {
            attendeeMemberIds = List.copyOf(attendeeMemberIds);
        }
    }
}
