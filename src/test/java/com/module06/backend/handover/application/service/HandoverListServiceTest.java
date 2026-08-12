package com.module06.backend.handover.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase.HandoverListQuery;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase.HandoverSummary;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverItem;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverListServiceTest {

    private static final Long HANDOVER_ID = 1000L;
    private static final Long MEMBER = 1L;
    private static final Long TEAM = 10L;
    private static final Long COMPANY = 100L;

    @Mock
    private HandoverRepository handoverRepository;

    @Mock
    private OrgQueryPort orgQueryPort;

    @InjectMocks
    private HandoverListService service;

    @Test
    void selfScopeQueriesByWriter() {
        when(handoverRepository.findByWriterMemberId(MEMBER)).thenReturn(List.of(handover()));

        List<HandoverSummary> result = service.list(HandoverListQuery.self(MEMBER, null));

        assertThat(result).hasSize(1);
        verifyNoInteractions(orgQueryPort);
    }

    @Test
    void teamScopeQueriesByTeam() {
        when(handoverRepository.findByTeamId(TEAM)).thenReturn(List.of(handover()));

        List<HandoverSummary> result = service.list(HandoverListQuery.team(TEAM, null));

        assertThat(result).hasSize(1);
        verifyNoInteractions(orgQueryPort);
    }

    @Test
    void companyScopeResolvesMemberIdsThroughOrgPortThenQueriesByWriterIn() {
        when(orgQueryPort.findMemberIdsByCompany(COMPANY)).thenReturn(List.of(1L, 2L, 3L));
        when(handoverRepository.findByWriterMemberIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(handover()));

        List<HandoverSummary> result = service.list(HandoverListQuery.company(COMPANY, null));

        assertThat(result).hasSize(1);
        verify(orgQueryPort).findMemberIdsByCompany(COMPANY);
        verify(handoverRepository).findByWriterMemberIdIn(List.of(1L, 2L, 3L));
    }

    @Test
    void companyScopeWithNoMembersReturnsEmptyWithoutHittingHandoverRepository() {
        when(orgQueryPort.findMemberIdsByCompany(COMPANY)).thenReturn(List.of());

        List<HandoverSummary> result = service.list(HandoverListQuery.company(COMPANY, null));

        assertThat(result).isEmpty();
        verify(handoverRepository, never()).findByWriterMemberIdIn(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void statusFilterKeepsOnlyMatchingStatus() {
        when(handoverRepository.findByWriterMemberId(MEMBER))
                .thenReturn(List.of(handover(HandoverStatus.SUBMITTED), handover(HandoverStatus.FINALIZED)));

        List<HandoverSummary> result = service.list(HandoverListQuery.self(MEMBER, HandoverStatus.FINALIZED));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(HandoverStatus.FINALIZED);
    }

    @Test
    void nullScopeIsRejectedLoudly() {
        assertThatThrownBy(() -> service.list(new HandoverListQuery(null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(HandoverErrorCode.HO_LIST_SCOPE_REQUIRED);
    }

    @Test
    void pendingAttributionReturnsOnlyPendingHandoversInCompany() {
        when(orgQueryPort.findMemberIdsByCompany(COMPANY)).thenReturn(List.of(1L, 2L, 3L));
        when(handoverRepository.findByWriterMemberIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(
                pendingAttributionOffboarding(),      // 귀속 대기 (FINALIZED offboarding, 미귀속)
                handover(HandoverStatus.FINALIZED),   // 휴직 FINALIZED — 대상 아님
                handover(HandoverStatus.SUBMITTED))); // 진행 중 — 대상 아님

        List<HandoverSummary> result = service.listPendingAttribution(COMPANY);

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.handoverType()).isEqualTo(HandoverType.OFFBOARDING);
            assertThat(summary.status()).isEqualTo(HandoverStatus.FINALIZED);
        });
    }

    @Test
    void pendingAttributionWithNoMembersReturnsEmptyWithoutHittingHandoverRepository() {
        when(orgQueryPort.findMemberIdsByCompany(COMPANY)).thenReturn(List.of());

        assertThat(service.listPendingAttribution(COMPANY)).isEmpty();
        verify(handoverRepository, never()).findByWriterMemberIdIn(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void pendingAttributionNullCompanyRejectedLoudly() {
        assertThatThrownBy(() -> service.listPendingAttribution(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(HandoverErrorCode.HO_COMPANY_CONTEXT_REQUIRED);
    }

    @Test
    void getReturnsHandoverById() {
        Handover handover = handover();
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.of(handover));

        assertThat(service.get(HANDOVER_ID)).isSameAs(handover);
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        when(handoverRepository.findById(HANDOVER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(HANDOVER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(HandoverErrorCode.HO_NOT_FOUND);
    }

    private static Handover handover() {
        return handover(HandoverStatus.SUBMITTED);
    }

    private static Handover handover(HandoverStatus status) {
        return Handover.restore(HANDOVER_ID, MEMBER, TEAM, HandoverType.VACATION, status,
                LocalDateTime.of(2026, 8, 10, 9, 0), LocalDateTime.of(2026, 8, 20, 18, 0), null,
                "Kim", "Manager", null, null, null, null, null, null, null, 1L, List.of(item()));
    }

    private static Handover pendingAttributionOffboarding() {
        // 팀장 오프보딩 직행 finalize 결과: FINALIZED이지만 필수 항목이 미귀속 → isPendingAttribution=true
        return Handover.restore(2000L, 2L, TEAM, HandoverType.OFFBOARDING, HandoverStatus.FINALIZED,
                null, null, LocalDate.of(2026, 8, 31),
                "Park", "Leader", null, null, null, null, LocalDateTime.of(2026, 8, 10, 12, 0),
                9L, "Owner", 1L, List.of(item()));
    }

    private static HandoverItem item() {
        return HandoverItem.create(100L, "Action", "TODO", "PRJ", "TEAM",
                LocalDate.of(2026, 8, 30), null, 500L, "Meeting", "Content",
                "Parent Action", LocalDate.of(2026, 8, 1), true);
    }
}
