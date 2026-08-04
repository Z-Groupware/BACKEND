package com.module06.backend.handover.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase.HandoverListQuery;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase.HandoverSummary;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverListServiceTest {

    private static final Long WRITER = 1L;
    private static final Long TEAM = 10L;
    private static final Long TARGET = 2L;
    private static final LocalDate START = LocalDate.of(2026, 8, 10);
    private static final LocalDate END = LocalDate.of(2026, 8, 20);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

    @Mock
    private HandoverRepository handoverRepository;

    private HandoverListService handoverListService;

    @BeforeEach
    void setUp() {
        handoverListService = new HandoverListService(handoverRepository);
    }

    @Test
    void listFailsWhenNoScopeGiven() {
        assertThatThrownBy(() -> handoverListService.list(new HandoverListQuery(null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HandoverErrorCode.HO_LIST_SCOPE_REQUIRED));
    }

    @Test
    void listFailsWhenBothScopesGiven() {
        assertThatThrownBy(() -> handoverListService.list(new HandoverListQuery(WRITER, TEAM, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HandoverErrorCode.HO_LIST_SCOPE_AMBIGUOUS));
    }

    @Test
    void listByWriterMapsSummaryWithReassignProgress() {
        when(handoverRepository.findByWriterMemberId(WRITER)).thenReturn(List.of(submittedWithOneReassignedItem()));

        List<HandoverSummary> result = handoverListService.list(new HandoverListQuery(WRITER, null, null));

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.writerMemberId()).isEqualTo(WRITER);
            assertThat(summary.writerName()).isEqualTo("Kim");
            assertThat(summary.handoverType()).isEqualTo(HandoverType.VACATION);
            assertThat(summary.leaveStartAt()).isEqualTo(START);
            assertThat(summary.leaveEndAt()).isEqualTo(END);
            assertThat(summary.itemCount()).isEqualTo(1);
            assertThat(summary.reassignRequiredCount()).isEqualTo(1);
            assertThat(summary.reassignedCount()).isEqualTo(1);
        });
    }

    @Test
    void listByTeamFiltersByStatus() {
        when(handoverRepository.findByTeamId(TEAM)).thenReturn(List.of(
                submittedWithOneReassignedItem(),
                finalized()));

        List<HandoverSummary> result = handoverListService.list(
                new HandoverListQuery(null, TEAM, HandoverStatus.FINALIZED));

        assertThat(result).singleElement()
                .satisfies(summary -> assertThat(summary.status()).isEqualTo(HandoverStatus.FINALIZED));
    }

    private static Handover submittedWithOneReassignedItem() {
        Handover handover = Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END, List.of(item(100L)));
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);
        return handover;
    }

    private static Handover finalized() {
        Handover handover = Handover.createVacation(WRITER, TEAM, "Kim", "Manager", START, END, List.of(item(100L)));
        handover.reassignItem(100L, TARGET, "Lee", "Staff", NOW);
        handover.complete(9L, "Park", NOW);
        handover.finalizeApproval(99L, "Owner", NOW);
        return handover;
    }

    private static HandoverItem item(Long actionId) {
        return HandoverItem.create(actionId, "Action", "TODO", "PRJ", "TEAM",
                LocalDate.of(2026, 8, 30), 500L, "Meeting", "Content", true);
    }
}
