package com.module06.backend.handover.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.application.port.out.MeetingQueryPort;
import com.module06.backend.meeting.application.result.MeetingAttendeeReferenceResult;
import com.module06.backend.meeting.application.result.MeetingHistoryResult;
import com.module06.backend.meeting.application.result.MeetingTopicResult;
import com.module06.backend.meeting.application.result.ProjectMeetingHistoryResult;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingTopicType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MeetingQueryPortDelegatingAdapterTest {

    private static final Long COMPANY_ID = 10L;

    @Mock
    private com.module06.backend.meeting.application.port.in.MeetingQueryPort meetingQueryPort;

    private MeetingQueryPortDelegatingAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MeetingQueryPortDelegatingAdapter(meetingQueryPort);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(1L, COMPANY_ID, "MEMBER", false, 2L),
                null,
                List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findMeetingMapsDResultToHandoverMeetingHistory() {
        LocalDateTime startAt = LocalDateTime.of(2026, 4, 3, 10, 30);
        MeetingHistoryResult result = new MeetingHistoryResult(
                100L,
                200L,
                "Weekly",
                MeetingStatus.DONE,
                startAt,
                LocalDateTime.of(2026, 4, 3, 11, 30),
                startAt,
                LocalDateTime.of(2026, 4, 3, 11, 20),
                3L,
                List.of(
                        new MeetingHistoryResult.Attendee(3L, "Host", "Platform"),
                        new MeetingHistoryResult.Attendee(4L, "Reviewer", "Product")
                )
        );
        when(meetingQueryPort.findMeeting(COMPANY_ID, 100L)).thenReturn(Optional.of(result));

        MeetingQueryPort.MeetingHistory mapped = adapter.findMeeting(100L);

        assertThat(mapped.meetingId()).isEqualTo(100L);
        assertThat(mapped.date()).isEqualTo(startAt.toLocalDate());
        assertThat(mapped.attendees()).containsExactly("Host", "Reviewer");
        assertThat(mapped.decisionSummary()).isNull();
        assertThat(mapped.actionItemsSummary()).isNull();
        verify(meetingQueryPort).findMeeting(COMPANY_ID, 100L);
    }

    @Test
    void findProjectMeetingsOrderedMapsProjectIdFromArgument() {
        LocalDateTime startAt = LocalDateTime.of(2026, 4, 4, 9, 0);
        when(meetingQueryPort.findProjectMeetingsOrdered(COMPANY_ID, 200L)).thenReturn(List.of(
                new ProjectMeetingHistoryResult(101L, "Kickoff", startAt, 3L, MeetingStatus.SCHEDULED)
        ));

        List<MeetingQueryPort.ProjectMeeting> mapped = adapter.findProjectMeetingsOrdered(200L);

        assertThat(mapped).containsExactly(new MeetingQueryPort.ProjectMeeting(101L, 200L, 3L, "Kickoff", startAt));
        verify(meetingQueryPort).findProjectMeetingsOrdered(COMPANY_ID, 200L);
    }

    @Test
    void findMeetingAttendeesMapsOneToOne() {
        List<Long> meetingIds = List.of(100L, 101L);
        when(meetingQueryPort.findMeetingAttendees(COMPANY_ID, meetingIds)).thenReturn(List.of(
                new MeetingAttendeeReferenceResult(100L, 3L),
                new MeetingAttendeeReferenceResult(101L, 4L)
        ));

        List<MeetingQueryPort.MeetingAttendee> mapped = adapter.findMeetingAttendees(meetingIds);

        assertThat(mapped).containsExactly(
                new MeetingQueryPort.MeetingAttendee(100L, 3L),
                new MeetingQueryPort.MeetingAttendee(101L, 4L)
        );
        verify(meetingQueryPort).findMeetingAttendees(COMPANY_ID, meetingIds);
    }

    @Test
    void findMeetingTopicsPropagatesTopicHierarchy() {
        List<Long> meetingIds = List.of(100L);
        when(meetingQueryPort.findMeetingTopics(COMPANY_ID, meetingIds)).thenReturn(List.of(
                new MeetingTopicResult(100L, 10L, null, MeetingTopicType.MAIN, "Decision", 1),
                new MeetingTopicResult(100L, 11L, 10L, MeetingTopicType.SUB, "Follow up", 2)
        ));

        List<MeetingQueryPort.MeetingTopic> mapped = adapter.findMeetingTopics(meetingIds);

        assertThat(mapped).containsExactly(
                new MeetingQueryPort.MeetingTopic(100L, 10L, null, "MAIN", "Decision", 1),
                new MeetingQueryPort.MeetingTopic(100L, 11L, 10L, "SUB", "Follow up", 2)
        );
        verify(meetingQueryPort).findMeetingTopics(COMPANY_ID, meetingIds);
    }

    @Test
    void findMeetingThrowsWhenDPortReturnsEmpty() {
        when(meetingQueryPort.findMeeting(COMPANY_ID, 404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.findMeeting(404L))
                .isInstanceOf(BusinessException.class);

        verify(meetingQueryPort).findMeeting(COMPANY_ID, 404L);
    }

    @Test
    void throwsWhenCompanyIdIsMissing() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(1L, null, "MEMBER", false, 2L),
                null,
                List.of()
        ));

        assertThatThrownBy(() -> adapter.findProjectMeetingsOrdered(200L))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(HandoverErrorCode.HO_COMPANY_CONTEXT_REQUIRED));

        verifyNoInteractions(meetingQueryPort);
    }
}
