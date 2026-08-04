package com.module06.backend.handover.domain.model;

import com.module06.backend.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandoverTest {

    private static final Long WRITER = 1L;
    private static final Long TEAM = 10L;
    private static final Long TARGET = 2L;
    private static final LocalDate START = LocalDate.of(2026, 8, 10);
    private static final LocalDate END = LocalDate.of(2026, 8, 20);
    private static final LocalDate LAST_WORKING_DAY = LocalDate.of(2026, 8, 31);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

    @Test
    void createVacationRequiresValidPeriod() {
        assertThatThrownBy(() -> Handover.createVacation(WRITER, TEAM, "Kim", "Manager", null, END, List.of()))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> Handover.createVacation(WRITER, TEAM, "Kim", "Manager", END, START, List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createVacationStartsSubmittedAndKeepsSnapshots() {
        Handover handover = Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END,
                List.of(item(100L)));

        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.SUBMITTED);
        assertThat(handover.getHandoverType()).isEqualTo(HandoverType.VACATION);
        assertThat(handover.getWriterNameSnap()).isEqualTo("Kim");
        assertThat(handover.getItems()).hasSize(1);
    }

    @Test
    void createOffboardingHasNoLeavePeriod() {
        Handover handover = Handover.createOffboarding(WRITER, TEAM, "Kim", "Manager",
                LAST_WORKING_DAY, List.of(item(100L)));

        assertThat(handover.getHandoverType()).isEqualTo(HandoverType.OFFBOARDING);
        assertThat(handover.getLeaveStartAt()).isNull();
        assertThat(handover.getLeaveEndAt()).isNull();
        assertThat(handover.getLastWorkingDay()).isEqualTo(LAST_WORKING_DAY);
    }

    @Test
    void completeRequiresAllRequiredItemsReassigned() {
        Handover handover = Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END,
                List.of(item(100L), item(101L)));
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);

        assertThatThrownBy(() -> handover.complete(9L, "Leader", NOW))
                .isInstanceOf(BusinessException.class);
        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.SUBMITTED);
    }

    @Test
    void completeIgnoresItemsThatDoNotRequireReassignment() {
        Handover handover = Handover.createOffboarding(WRITER, TEAM, "Kim", "Manager", LAST_WORKING_DAY,
                List.of(item(100L), completedItem(101L)));
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);

        handover.complete(9L, "Leader", NOW);

        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
    }

    @Test
    void completeStillRejectsWhenRequiredItemIsNotReassigned() {
        Handover handover = Handover.createOffboarding(WRITER, TEAM, "Kim", "Manager", LAST_WORKING_DAY,
                List.of(item(100L), completedItem(101L)));

        assertThatThrownBy(() -> handover.complete(9L, "Leader", NOW))
                .isInstanceOf(BusinessException.class);
        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.SUBMITTED);
    }

    @Test
    void completeMovesToReassignedAndStoresApproverSnapshot() {
        Handover handover = Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END,
                List.of(item(100L)));
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);

        handover.complete(9L, "Leader", NOW);

        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
        assertThat(handover.getIntermediateApproverId()).isEqualTo(9L);
        assertThat(handover.getIntermediateApproverNameSnap()).isEqualTo("Leader");
        assertThat(handover.getIntermediateApprovedAt()).isEqualTo(NOW);
    }

    @Test
    void reassignRejectedWhenNotSubmitted() {
        Handover handover = reassignedHandover();

        assertThatThrownBy(() -> handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeRequiresReassignedAndStoresTime() {
        Handover submitted = Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END, List.of());
        assertThatThrownBy(() -> submitted.finalizeApproval(9L, "Leader", NOW))
                .isInstanceOf(BusinessException.class);

        Handover reassigned = reassignedHandover();
        reassigned.finalizeApproval(9L, "Leader", NOW);

        assertThat(reassigned.getStatus()).isEqualTo(HandoverStatus.FINALIZED);
        assertThat(reassigned.getFinalizedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectRequiresReasonAndCannotRejectFinalized() {
        Handover handover = Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END, List.of());

        assertThatThrownBy(() -> handover.reject(" "))
                .isInstanceOf(BusinessException.class);

        handover.reject("missing details");
        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.REJECTED);
        assertThat(handover.getRejectReason()).isEqualTo("missing details");

        Handover finalized = reassignedHandover();
        finalized.finalizeApproval(9L, "Leader", NOW);
        assertThatThrownBy(() -> finalized.reject("late"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void noItemsCountsAsAllReassigned() {
        Handover handover = Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END, List.of());

        assertThat(handover.isAllReassigned()).isTrue();
        handover.complete(9L, "Leader", NOW);
        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
    }

    private static Handover reassignedHandover() {
        Handover handover = Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END,
                List.of(item(100L)));
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);
        handover.complete(9L, "Leader", NOW);
        return handover;
    }

    private static HandoverItem item(Long actionId) {
        return HandoverItem.create(actionId, "Action " + actionId, "TODO", "PRJ", "TEAM",
                LocalDate.of(2026, 8, 30), 300L, "Weekly", "Context " + actionId, true);
    }

    private static HandoverItem completedItem(Long actionId) {
        return HandoverItem.create(actionId, "Action " + actionId, "DONE", "PRJ", "TEAM",
                LocalDate.of(2026, 8, 30), 300L, "Weekly", "Context " + actionId, false);
    }
}
