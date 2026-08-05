package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * E 인수인계의 출처 회의 히스토리에 제공할 D도메인 내부 단건 조회 결과다.
 *
 * E가 정의할 반환 DTO와 직접 결합하지 않고 D의 실제 회의 필드를 안정적인 애플리케이션 결과로 제공한다.
 */
public record MeetingHistoryResult(
        Long meetingId,
        Long projectId,
        String title,
        MeetingStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long hostMemberId,
        List<Attendee> attendees
) {

    /* 참석자 스냅샷 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public MeetingHistoryResult {
        /* E finalize 시점의 값이 조립 도중 변경되지 않도록 방어적으로 복사한다. */
        attendees = List.copyOf(attendees);
    }

    /* 인수인계 스냅샷에 필요한 참석자 식별자와 표시 정보다. */
    public record Attendee(Long memberId, String name, String teamName) {
    }
}
