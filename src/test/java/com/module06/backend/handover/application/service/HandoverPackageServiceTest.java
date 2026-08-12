package com.module06.backend.handover.application.service;

import com.module06.backend.handover.application.port.out.MeetingQueryPort;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverItem;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverPackageServiceTest {

    private static final Long HANDOVER_ID = 1000L;
    private static final Long WRITER = 1L;
    private static final Long TEAM = 10L;
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 3);
    private static final LocalDate LAST_WORKING_DAY = LocalDate.of(2026, 8, 31);
    private static final LocalDateTime ACTION_CREATED_AT = LocalDateTime.of(2026, 7, 20, 9, 0);

    @Mock
    private HandoverRepository handoverRepository;

    @Mock
    private MeetingQueryPort meetingQueryPort;

    private HandoverPackageService handoverPackageService;

    @BeforeEach
    void setUp() {
        handoverPackageService = new HandoverPackageService(handoverRepository, meetingQueryPort);
    }

    @Test
    void assemblesPackageFromSnapshotsAndDistinctMeetingHistory() {
        Handover handover = Handover.restore(HANDOVER_ID, WRITER, TEAM, HandoverType.OFFBOARDING,
                HandoverStatus.SUBMITTED, null, null, LAST_WORKING_DAY, "Kim", "Manager",
                null, null, null, null, null,
                null, null, null,
                List.of(
                        item(100L, "Prepare runbook", "TODO", REFERENCE_DATE.plusDays(7),
                                700L, "Weekly Sync", "Runbook context", 2L, "Lee"),
                        item(101L, "Close billing", "DONE", REFERENCE_DATE.plusDays(1),
                                700L, "Weekly Sync", "Billing context", 2L, "Lee"),
                        item(102L, "Update access", "IN_PROGRESS", REFERENCE_DATE,
                                701L, "Security Review", "Access context", 3L, "Choi"),
                        item(103L, "Archive docs", "TODO", REFERENCE_DATE.plusDays(8),
                                null, null, "Archive context", null, null)
                ));
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));
        when(meetingQueryPort.findMeeting(700L)).thenReturn(new MeetingQueryPort.MeetingHistory(
                700L, LocalDate.of(2026, 8, 1), List.of("Kim", "Lee"), "Decision A", "Actions A"));
        when(meetingQueryPort.findMeeting(701L)).thenReturn(new MeetingQueryPort.MeetingHistory(
                701L, LocalDate.of(2026, 8, 2), List.of("Kim", "Choi"), "Decision B", "Actions B"));

        GetHandoverPackageUseCase.HandoverPackage result =
                handoverPackageService.getPackage(HANDOVER_ID, REFERENCE_DATE);

        assertThat(result.basicInfo().writerName()).isEqualTo("Kim");
        assertThat(result.basicInfo().absenceType()).isEqualTo(HandoverType.OFFBOARDING);
        assertThat(result.basicInfo().lastWorkingDay()).isEqualTo(LAST_WORKING_DAY);
        assertThat(result.gapSummary().totalItems()).isEqualTo(4);
        assertThat(result.gapSummary().incompleteCount()).isEqualTo(3);
        assertThat(result.gapSummary().dueSoonCount()).isEqualTo(2);
        assertThat(result.items()).extracting(GetHandoverPackageUseCase.Item::title)
                .containsExactly("Prepare runbook", "Close billing", "Update access", "Archive docs");
        assertThat(result.items().get(0).deadline()).isEqualTo(REFERENCE_DATE.plusDays(7));
        assertThat(result.items().get(0).startAt()).isEqualTo(ACTION_CREATED_AT.toLocalDate());
        assertThat(result.items().get(0).sourceMeetingTitle()).isEqualTo("Weekly Sync");
        assertThat(result.contextCards()).extracting(GetHandoverPackageUseCase.ContextCard::contentSnap)
                .containsExactly("Runbook context", "Billing context", "Access context", "Archive context");
        assertThat(result.meetingHistories()).extracting(GetHandoverPackageUseCase.MeetingHistory::meetingId)
                .containsExactly(700L, 701L);
        assertThat(result.reassigneeGroups()).hasSize(3);
        assertThat(result.reassigneeGroups().get(0).reassigneeName()).isEqualTo("Lee");
        assertThat(result.reassigneeGroups().get(0).items()).hasSize(2);
        assertThat(result.reassigneeGroups().get(2).reassigneeName()).isEqualTo("미배정");
        assertThat(result.reassigneeGroups().get(2).items()).singleElement()
                .extracting(GetHandoverPackageUseCase.Item::actionId)
                .isEqualTo(103L);
        verify(meetingQueryPort, times(1)).findMeeting(700L);
        verify(meetingQueryPort, times(1)).findMeeting(701L);
    }

    private static HandoverItem item(Long actionId, String title, String status, LocalDate deadline,
                                     Long meetingId, String meetingTitle, String content,
                                     Long reassigneeId, String reassigneeName) {
        return new HandoverItem(null, actionId, title, status, "PRJ", "TEAM", deadline,
                ACTION_CREATED_AT, meetingId, meetingTitle, content,
                "Parent Action", LocalDate.of(2026, 8, 1),
                reassigneeId, reassigneeName,
                reassigneeId == null ? null : "Staff", null, null, null, true);
    }
}
