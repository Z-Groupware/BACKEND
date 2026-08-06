package com.module06.backend.handover.infrastructure.adapter;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.handover.application.port.out.MeetingQueryPort;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.meeting.application.result.MeetingAttendeeReferenceResult;
import com.module06.backend.meeting.application.result.MeetingHistoryResult;
import com.module06.backend.meeting.application.result.MeetingTopicResult;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MeetingQueryPortDelegatingAdapter implements MeetingQueryPort {

    private final com.module06.backend.meeting.application.port.in.MeetingQueryPort meetingQueryPort;

    public MeetingQueryPortDelegatingAdapter(
            com.module06.backend.meeting.application.port.in.MeetingQueryPort meetingQueryPort
    ) {
        this.meetingQueryPort = meetingQueryPort;
    }

    @Override
    public MeetingHistory findMeeting(Long meetingId) {
        Long companyId = currentCompanyId();
        MeetingHistoryResult meeting = meetingQueryPort.findMeeting(companyId, meetingId)
                .orElseThrow(() -> new BusinessException(HandoverErrorCode.HO_NOT_FOUND));

        return new MeetingHistory(
                meeting.meetingId(),
                meeting.startAt().toLocalDate(),
                meeting.attendees().stream()
                        .map(MeetingHistoryResult.Attendee::name)
                        .toList(),
                // A/C domains own decision and action summaries; D result from PR#66 does not expose them yet.
                null,
                // Intentional null until A/C integration is wired, not a silent empty-value fallback.
                null
        );
    }

    @Override
    public List<ProjectMeeting> findProjectMeetingsOrdered(Long projectId) {
        Long companyId = currentCompanyId();
        return meetingQueryPort.findProjectMeetingsOrdered(companyId, projectId).stream()
                .map(result -> new ProjectMeeting(
                        result.meetingId(),
                        projectId,
                        result.hostMemberId(),
                        result.title(),
                        result.startAt()
                ))
                .toList();
    }

    @Override
    public List<MeetingAttendee> findMeetingAttendees(List<Long> meetingIds) {
        Long companyId = currentCompanyId();
        return meetingQueryPort.findMeetingAttendees(companyId, meetingIds).stream()
                .map(this::toMeetingAttendee)
                .toList();
    }

    @Override
    public List<MeetingTopic> findMeetingTopics(List<Long> meetingIds) {
        Long companyId = currentCompanyId();
        return meetingQueryPort.findMeetingTopics(companyId, meetingIds).stream()
                .map(this::toMeetingTopic)
                .toList();
    }

    private MeetingAttendee toMeetingAttendee(MeetingAttendeeReferenceResult result) {
        return new MeetingAttendee(result.meetingId(), result.memberId());
    }

    private MeetingTopic toMeetingTopic(MeetingTopicResult result) {
        return new MeetingTopic(
                result.meetingId(),
                result.topicId(),
                result.parentTopicId(),
                result.type().name(),
                result.content(),
                result.sortOrder()
        );
    }

    private Long currentCompanyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof AuthPrincipal authPrincipal) || authPrincipal.getCompanyId() == null) {
            // 알려진 미구현 대기 신호: auth(B) 도입 전이라 요청 시점 AuthPrincipal companyId가 없을 수 있다.
            // IllegalState는 GlobalExceptionHandler를 못 타 500으로 샌다(브리프4 PartB) → 타입화된 BusinessException으로 던진다.
            // silent fallback이 아니라 "auth(B) 대기 중"이라는 명시적 신호.
            throw new BusinessException(HandoverErrorCode.HO_COMPANY_CONTEXT_REQUIRED);
        }
        return authPrincipal.getCompanyId();
    }
}
