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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 이 테스트의 핵심은 "회사 경계가 토큰의 companyId 로만 선다"를 고정하는 것이다. handover 테이블엔
 * company_id 가 없어(2026-08-06 결정) 서비스가 매번 회사 구성원을 물어 그 집합으로 거른다 —
 * 이 관문이 없으면 로그인한 사원이 남의 회사 teamId·사번을 넣어 목록을 읽을 수 있다.
 */
@DisplayName("HandoverListService")
class HandoverListServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long IN_COMPANY_WRITER = 10L;
    private static final Long OTHER_COMPANY_WRITER = 999L;
    private static final Long TEAM = 100L;

    private HandoverRepository handoverRepository;
    private OrgQueryPort orgQueryPort;
    private HandoverListService service;

    @BeforeEach
    void setUp() {
        handoverRepository = mock(HandoverRepository.class);
        orgQueryPort = mock(OrgQueryPort.class);
        service = new HandoverListService(handoverRepository, orgQueryPort);
    }

    @Test
    @DisplayName("사번 스코프: 회사 구성원의 인수인계는 조회된다")
    void byWriterReturnsWhenWriterInCompany() {
        when(orgQueryPort.findMemberIdsByCompany(COMPANY)).thenReturn(List.of(IN_COMPANY_WRITER));
        when(handoverRepository.findByWriterMemberId(IN_COMPANY_WRITER))
                .thenReturn(List.of(handover(1L, IN_COMPANY_WRITER, TEAM)));

        List<HandoverSummary> result = service.list(
                new HandoverListQuery(COMPANY, IN_COMPANY_WRITER, null, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).writerMemberId()).isEqualTo(IN_COMPANY_WRITER);
    }

    @Test
    @DisplayName("사번 스코프: 남의 회사 사번은 조회가 아니라 월경이라 403 으로 막는다")
    void byWriterForbiddenWhenWriterOutsideCompany() {
        when(orgQueryPort.findMemberIdsByCompany(COMPANY)).thenReturn(List.of(IN_COMPANY_WRITER));

        assertThatThrownBy(() -> service.list(
                new HandoverListQuery(COMPANY, OTHER_COMPANY_WRITER, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", HandoverErrorCode.HO_LIST_SCOPE_FORBIDDEN);

        verify(handoverRepository, never()).findByWriterMemberId(any());
    }

    @Test
    @DisplayName("팀 스코프: 남의 회사 구성원이 쓴 행은 걸러진다 — 남의 회사 teamId 는 빈 목록")
    void byTeamFiltersOutForeignWriters() {
        when(orgQueryPort.findMemberIdsByCompany(COMPANY)).thenReturn(List.of(IN_COMPANY_WRITER));
        when(handoverRepository.findByTeamId(TEAM)).thenReturn(List.of(
                handover(1L, IN_COMPANY_WRITER, TEAM),
                handover(2L, OTHER_COMPANY_WRITER, TEAM)));

        List<HandoverSummary> result = service.list(
                new HandoverListQuery(COMPANY, null, TEAM, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).writerMemberId()).isEqualTo(IN_COMPANY_WRITER);
    }

    @Test
    @DisplayName("companyId 가 없으면 회사 경계를 세울 수 없어 조기 실패한다")
    void companyIdRequired() {
        assertThatThrownBy(() -> service.list(new HandoverListQuery(null, IN_COMPANY_WRITER, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", HandoverErrorCode.HO_LIST_SCOPE_REQUIRED);
    }

    @Test
    @DisplayName("사번·팀 둘 다 없으면 범위 필요 에러")
    void scopeRequired() {
        assertThatThrownBy(() -> service.list(new HandoverListQuery(COMPANY, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", HandoverErrorCode.HO_LIST_SCOPE_REQUIRED);
    }

    @Test
    @DisplayName("사번·팀 둘 다 있으면 범위 모호 에러")
    void scopeAmbiguous() {
        assertThatThrownBy(() -> service.list(new HandoverListQuery(COMPANY, IN_COMPANY_WRITER, TEAM, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", HandoverErrorCode.HO_LIST_SCOPE_AMBIGUOUS);
    }

    private static Handover handover(Long id, Long writerMemberId, Long teamId) {
        HandoverItem item = HandoverItem.create(500L, "Action", "TODO", "PRJ", "TEAM",
                LocalDate.of(2026, 8, 30), 700L, "Meeting", "Content", true);
        return Handover.restore(id, writerMemberId, teamId, HandoverType.VACATION, HandoverStatus.SUBMITTED,
                LocalDateTime.of(2026, 8, 10, 9, 0), LocalDateTime.of(2026, 8, 20, 18, 0), null,
                "Writer", "Position", null, null, null, null, null,
                null, null, 1L, List.of(item));
    }
}
