package com.module06.backend.meeting.application.result;

import java.time.LocalDate;
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
        List<Attendee> attendees,
        List<DecisionSummary> decisions,
        List<ActionSummary> actions
) {

    /* 출처 회의 카드의 목록 값이 생성 이후 변경되지 않도록 방어적으로 복사한다. */
    public MeetingHistoryResult {
        /* E finalize 시점의 값이 조립 도중 변경되지 않도록 방어적으로 복사한다. */
        attendees = List.copyOf(attendees);

        /* A 연동 전의 의도적 미제공 상태는 null로 유지하고, 제공된 결정 목록만 불변으로 만든다. */
        decisions = decisions == null ? null : List.copyOf(decisions);

        /* C 연동 전의 의도적 미제공 상태는 null로 유지하고, 제공된 액션 목록만 불변으로 만든다. */
        actions = actions == null ? null : List.copyOf(actions);
    }

    /* A/C 상류 계약이 연결되기 전 기존 회의 조회 호출부와의 호환성을 유지한다. */
    public MeetingHistoryResult(
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
        /* null은 빈 결과가 아니라 해당 상류 요약 계약이 아직 연결되지 않았음을 뜻한다. */
        this(
                meetingId,
                projectId,
                title,
                status,
                startAt,
                endAt,
                startedAt,
                endedAt,
                hostMemberId,
                attendees,
                null,
                null
        );
    }

    /* 인수인계 스냅샷에 필요한 참석자 식별자와 표시 정보다. */
    public record Attendee(Long memberId, String name, String teamName) {
    }

    /* A 분석 도메인에서 읽어 출처 회의 카드에 표시할 확정 결정 요약이다. */
    public record DecisionSummary(Long decisionId, String content, String reason) {
    }

    /* C 액션 도메인에서 읽어 출처 회의 카드에 표시할 현재 액션 요약이다. */
    public record ActionSummary(
            Long actionId,
            String actionType,
            String title,
            String status,
            LocalDate dueDate,
            String assigneeName,
            String teamName
    ) {
    }
}
