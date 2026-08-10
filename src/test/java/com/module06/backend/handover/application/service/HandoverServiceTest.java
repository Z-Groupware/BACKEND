package com.module06.backend.handover.application.service;

import com.module06.backend.handover.application.command.CreateHandoverCommand;
import com.module06.backend.handover.application.command.FinalizeHandoverInsightsCommand;
import com.module06.backend.handover.application.command.ReassignItemCommand;
import com.module06.backend.handover.application.command.RejectHandoverCommand;
import com.module06.backend.handover.application.port.out.ActionReassignPort;
import com.module06.backend.handover.application.port.out.MemberStatusPort;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.application.usecase.FinalizeHandoverInsightsUseCase;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverServiceTest {

    private static final Long HANDOVER_ID = 1000L;
    private static final Long WRITER = 1L;
    private static final Long TEAM = 10L;
    private static final Long TARGET = 2L;
    private static final Long LEADER = 9L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 10, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 8, 20, 18, 0);
    private static final LocalDate LAST_WORKING_DAY = LocalDate.of(2026, 8, 31);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

    @Mock
    private HandoverRepository handoverRepository;

    @Mock
    private ActionReassignPort actionReassignPort;

    @Mock
    private OrgQueryPort orgQueryPort;

    @Mock
    private MemberStatusPort memberStatusPort;

    @Mock
    private FinalizeHandoverInsightsUseCase finalizeHandoverInsightsUseCase;

    private HandoverService handoverService;

    @BeforeEach
    void setUp() {
        handoverService = new HandoverService(handoverRepository, actionReassignPort, orgQueryPort, memberStatusPort,
                finalizeHandoverInsightsUseCase);
        lenient().when(handoverRepository.save(any(Handover.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(orgQueryPort.findTeamName(TEAM)).thenReturn("Platform Team");
    }

    @Test
    void createVacationSnapshotsWriterAndSelectedActionsAndMovesWriterToWaiting() {
        when(orgQueryPort.findMember(WRITER)).thenReturn(new OrgQueryPort.MemberSnapshot(WRITER, "Kim", "Manager"));
        when(actionReassignPort.findHandoverableActions(WRITER, HandoverType.VACATION)).thenReturn(List.of(
                action(100L, "A", "TODO"),
                action(101L, "B", "IN_PROGRESS")
        ));

        Handover handover = handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.VACATION, START, END, null, "인계 서술", List.of(101L)
        ));

        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.SUBMITTED);
        assertThat(handover.getNote()).isEqualTo("인계 서술");
        assertThat(handover.getWriterNameSnap()).isEqualTo("Kim");
        assertThat(handover.getTeamNameSnap()).isEqualTo("Platform Team");
        assertThat(handover.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getActionId()).isEqualTo(101L);
                    assertThat(item.getActionTitleSnap()).isEqualTo("B");
                    assertThat(item.getActionStatusSnap()).isEqualTo("IN_PROGRESS");
                    assertThat(item.getDeadlineSnap()).isEqualTo(LocalDate.of(2026, 8, 30));
                    assertThat(item.getSourceMeetingId()).isEqualTo(501L);
                    assertThat(item.getSourceMeetingTitleSnap()).isEqualTo("Meeting B");
                    assertThat(item.getContentSnap()).isEqualTo("Content B");
                    assertThat(item.isReassignRequired()).isTrue();
                });
        verify(memberStatusPort).toWaiting(WRITER);
        verify(orgQueryPort).findTeamName(TEAM);
    }

    @Test
    void createSnapshotsTeamNameFromOrgPort() {
        when(orgQueryPort.findMember(WRITER)).thenReturn(new OrgQueryPort.MemberSnapshot(WRITER, "Kim", "Manager"));
        when(orgQueryPort.findTeamName(TEAM)).thenReturn("PDF Team");
        when(actionReassignPort.findHandoverableActions(WRITER, HandoverType.VACATION))
                .thenReturn(List.of(action(100L, "A", "TODO")));

        Handover handover = handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.VACATION, START, END, null, null, List.of(100L)
        ));

        assertThat(handover.getTeamNameSnap()).isEqualTo("PDF Team");
        verify(orgQueryPort).findTeamName(TEAM);
    }

    @Test
    void createOffboardingSnapshotsAllActions() {
        when(orgQueryPort.findMember(WRITER)).thenReturn(new OrgQueryPort.MemberSnapshot(WRITER, "Kim", "Manager"));
        when(actionReassignPort.findHandoverableActions(WRITER, HandoverType.OFFBOARDING)).thenReturn(List.of(
                action(100L, "A", "TODO"),
                action(101L, "B", "IN_PROGRESS")
        ));

        Handover handover = handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.OFFBOARDING, null, null, LAST_WORKING_DAY, null, List.of(101L)
        ));

        assertThat(handover.getHandoverType()).isEqualTo(HandoverType.OFFBOARDING);
        assertThat(handover.getLastWorkingDay()).isEqualTo(LAST_WORKING_DAY);
        assertThat(handover.getItems()).extracting(HandoverItem::getActionId).containsExactly(100L, 101L);
        assertThat(handover.getItems()).extracting(HandoverItem::isReassignRequired).containsExactly(true, true);
        verify(memberStatusPort).toWaiting(WRITER);
    }

    @Test
    void createOffboardingMarksCompletedActionsAsNotReassignRequired() {
        when(orgQueryPort.findMember(WRITER)).thenReturn(new OrgQueryPort.MemberSnapshot(WRITER, "Kim", "Manager"));
        when(actionReassignPort.findHandoverableActions(WRITER, HandoverType.OFFBOARDING)).thenReturn(List.of(
                action(100L, "A", "TODO"),
                action(101L, "B", "DONE")
        ));

        Handover handover = handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.OFFBOARDING, null, null, LAST_WORKING_DAY, null, null
        ));

        assertThat(handover.getItems()).extracting(HandoverItem::isReassignRequired).containsExactly(true, false);
    }

    @Test
    void createRejectedWhenWriterAlreadyHasActiveHandover() {
        when(handoverRepository.existsActiveByWriter(WRITER)).thenReturn(true);

        assertThatThrownBy(() -> handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.VACATION, START, END, null, null, List.of(101L))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(HandoverErrorCode.HO_ACTIVE_ALREADY_EXISTS));

        verify(memberStatusPort, never()).toWaiting(WRITER);
    }

    @Test
    void createVacationFailsWhenSelectedActionIdNotHandoverable() {
        when(orgQueryPort.findMember(WRITER)).thenReturn(new OrgQueryPort.MemberSnapshot(WRITER, "Kim", "Manager"));
        when(actionReassignPort.findHandoverableActions(WRITER, HandoverType.VACATION))
                .thenReturn(List.of(action(100L, "A", "TODO")));

        assertThatThrownBy(() -> handoverService.create(new CreateHandoverCommand(
                        WRITER, TEAM, HandoverType.VACATION, START, END, null, null, List.of(100L, 999L))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(HandoverErrorCode.HO_SELECTED_ACTION_NOT_HANDOVERABLE));
    }

    @Test
    void reassignItemSnapshotsReassignTarget() {
        Handover handover = submittedWithOneItem();
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));
        when(orgQueryPort.findMember(TARGET)).thenReturn(new OrgQueryPort.MemberSnapshot(TARGET, "Lee", "Staff"));

        Handover saved = handoverService.reassignItem(new ReassignItemCommand(HANDOVER_ID, 100L, TARGET, NOW));

        HandoverItem item = saved.getItems().get(0);
        assertThat(item.getReassigneeId()).isEqualTo(TARGET);
        assertThat(item.getReassigneeNameSnap()).isEqualTo("Lee");
        assertThat(item.getReassigneePositionSnap()).isEqualTo("Staff");
        assertThat(item.getReassignedAt()).isEqualTo(NOW);
    }

    @Test
    void completeCommitsEachActionReassignmentAndMovesToReassigned() {
        Handover handover = submittedWithOneItem();
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));
        when(orgQueryPort.findMember(LEADER)).thenReturn(new OrgQueryPort.MemberSnapshot(LEADER, "Park", "Leader"));

        Handover saved = handoverService.complete(HANDOVER_ID, LEADER, NOW);

        assertThat(saved.getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
        assertThat(saved.getIntermediateApproverId()).isEqualTo(LEADER);
        assertThat(saved.getIntermediateApproverNameSnap()).isEqualTo("Park");
        assertThat(saved.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getCommittedAt()).isEqualTo(NOW);
                    assertThat(item.isCommitted()).isTrue();
                });
        verify(actionReassignPort).reassign(100L, WRITER, TARGET);
    }

    @Test
    void completeDoesNotCommitItemsThatDoNotRequireReassignment() {
        Handover handover = Handover.createOffboarding(WRITER, TEAM, "Kim", "Manager", LAST_WORKING_DAY,
                List.of(item(100L), completedItem(101L)));
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));
        when(orgQueryPort.findMember(LEADER)).thenReturn(new OrgQueryPort.MemberSnapshot(LEADER, "Park", "Leader"));

        Handover saved = handoverService.complete(HANDOVER_ID, LEADER, NOW);

        assertThat(saved.getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
        assertThat(saved.getItems()).extracting(HandoverItem::getCommittedAt).containsExactly(NOW, null);
        verify(actionReassignPort).reassign(100L, WRITER, TARGET);
        // 재분배 불필요 항목(101L)에 대한 어떤 상호작용도 없어야 함 — never(특정 인자)보다 넓게 봉쇄.
        verifyNoMoreInteractions(actionReassignPort);
    }

    @Test
    void finalizeVacationMovesWriterToVacation() {
        Handover handover = reassigned(HandoverType.VACATION);
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));

        Handover saved = handoverService.finalize(HANDOVER_ID, LEADER, "Park", NOW);

        assertThat(saved.getStatus()).isEqualTo(HandoverStatus.FINALIZED);
        assertThat(saved.getFinalApproverId()).isEqualTo(LEADER);
        assertThat(saved.getFinalApproverNameSnap()).isEqualTo("Park");
        verify(memberStatusPort).toVacation(WRITER);
        verify(memberStatusPort, never()).offboard(WRITER);
        verifyNoInteractions(finalizeHandoverInsightsUseCase);
    }

    @Test
    void finalizeOffboardingOffboardsWriter() {
        Handover handover = reassigned(HandoverType.OFFBOARDING);
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));

        Handover saved = handoverService.finalize(HANDOVER_ID, LEADER, "Park", NOW);

        assertThat(saved.getStatus()).isEqualTo(HandoverStatus.FINALIZED);
        verify(memberStatusPort).offboard(WRITER);
        verify(memberStatusPort, never()).toVacation(WRITER);
        verify(finalizeHandoverInsightsUseCase)
                .finalizeInsights(new FinalizeHandoverInsightsCommand(HANDOVER_ID, WRITER));
    }

    @Test
    void rejectStoresReasonAndRestoresWriterActive() {
        Handover handover = submittedWithOneItem();
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));

        Handover saved = handoverService.reject(new RejectHandoverCommand(HANDOVER_ID, "needs more detail"));

        assertThat(saved.getStatus()).isEqualTo(HandoverStatus.REJECTED);
        assertThat(saved.getRejectReason()).isEqualTo("needs more detail");
        verify(memberStatusPort).restoreActive(WRITER);
        verify(actionReassignPort, never()).rollbackReassignment(any(), any(), any());
    }

    @Test
    void rejectAfterCompleteRollsBackCommittedReassignmentsToWriterAndRestoresWriterActive() {
        Handover handover = submittedWithOneItem();
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));
        when(orgQueryPort.findMember(LEADER)).thenReturn(new OrgQueryPort.MemberSnapshot(LEADER, "Park", "Leader"));

        handoverService.complete(HANDOVER_ID, LEADER, NOW);
        Handover saved = handoverService.reject(new RejectHandoverCommand(HANDOVER_ID, "not enough"));

        assertThat(saved.getStatus()).isEqualTo(HandoverStatus.REJECTED);
        assertThat(saved.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getRollbackStatus()).isEqualTo("ROLLED_BACK"));
        verify(actionReassignPort).reassign(100L, WRITER, TARGET);
        verify(actionReassignPort).rollbackReassignment(100L, TARGET, WRITER);
        verify(memberStatusPort).restoreActive(WRITER);
    }

    @Test
    void rejectSubmittedHandoverDoesNotRollbackUncommittedReassignments() {
        Handover handover = submittedWithOneItem();
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));

        Handover saved = handoverService.reject(new RejectHandoverCommand(HANDOVER_ID, "try again"));

        assertThat(saved.getStatus()).isEqualTo(HandoverStatus.REJECTED);
        assertThat(saved.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getRollbackStatus()).isNull());
        verify(actionReassignPort, never()).rollbackReassignment(any(), any(), any());
    }

    private static ActionReassignPort.HandoverableAction action(Long id, String title, String status) {
        return new ActionReassignPort.HandoverableAction(id, title, "PRJ", 700L, "TEAM", status,
                LocalDate.of(2026, 8, 30), null, 500L + (id - 100L), "Meeting " + title, "Content " + title);
    }

    private static Handover submittedWithOneItem() {
        return Handover.restore(HANDOVER_ID, WRITER, TEAM, HandoverType.VACATION, HandoverStatus.SUBMITTED,
                START, END, null, "Kim", "Manager", null, null, null, null, null,
                null, null, null,
                List.of(item(100L)));
    }

    private static Handover reassigned(HandoverType type) {
        Handover handover = type == HandoverType.VACATION
                ? Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END,
                        List.of(item(100L)))
                : Handover.createOffboarding(WRITER, TEAM, "Kim", "Manager", LAST_WORKING_DAY,
                        List.of(item(100L)));
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);
        handover.complete(LEADER, "Park", NOW);
        return handover;
    }

    private static HandoverItem item(Long actionId) {
        return HandoverItem.create(actionId, "Action", "TODO", "PRJ", "TEAM",
                LocalDate.of(2026, 8, 30), null, 500L, "Meeting", "Content", true);
    }

    private static HandoverItem completedItem(Long actionId) {
        return HandoverItem.create(actionId, "Action", "DONE", "PRJ", "TEAM",
                LocalDate.of(2026, 8, 30), null, 500L, "Meeting", "Content", false);
    }
}
