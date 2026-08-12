package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.MeteringDashboardResult;
import com.module06.backend.metering.application.result.TeamMeteringDashboardResult;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.model.QuotaStatus;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeteringDashboardServiceTest {

    private static final Long COMPANY = 1L;
    // 교차 회사 격리 검증용 — COMPANY 와 다른 회사.
    private static final Long OTHER_COMPANY = 2L;
    private static final LocalDateTime AUGUST_START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime SEPTEMBER_START = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Mock
    private TokenUsageRecordRepository tokenUsageRecordRepository;

    @Mock
    private CompanyTokenPlanRepository companyTokenPlanRepository;

    private MeteringDashboardService service;

    // 고정 Clock — period 미지정 시 월 판정이 실행 시각에 흔들리지 않게 한다(2026-08 고정).
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        service = new MeteringDashboardService(tokenUsageRecordRepository, companyTokenPlanRepository, FIXED_CLOCK);
    }

    @Test
    void dashboardCalculatesCompanyUsageDepartmentBreakdownAndDirectionalAmount() {
        when(companyTokenPlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(plan()));
        when(tokenUsageRecordRepository.sumTotalTokens(COMPANY, AUGUST_START, SEPTEMBER_START))
                .thenReturn(1_502_300L);
        // 회사 전체 방향 합계(입력 1,000,000 · 출력 502,300, 합 = 총량과 일치).
        when(tokenUsageRecordRepository.sumDirectionTokens(COMPANY, AUGUST_START, SEPTEMBER_START))
                .thenReturn(new TokenUsageRecordRepository.DirectionUsageAggregate(1_000_000L, 502_300L));
        when(tokenUsageRecordRepository.sumTotalTokensByDepartment(COMPANY, AUGUST_START, SEPTEMBER_START))
                .thenReturn(List.of(
                        new TokenUsageRecordRepository.DepartmentUsageAggregate(10L, 1_000L, 1_000L, 0L),
                        new TokenUsageRecordRepository.DepartmentUsageAggregate(20L, 1_501_300L, 999_000L, 502_300L)
                ));

        MeteringDashboardResult result = service.getCompanyDashboard(owner(), "2026-08");

        assertThat(result.usedTokens()).isEqualTo(1_502_300L);
        assertThat(result.monthlyTokenPool()).isEqualTo(1_500_000L);
        assertThat(result.overageTokens()).isEqualTo(2_300L);
        // estimatedAmountKrw 는 총량 기준(overage 20원/1k): baseFee 150,000 + ceil(2,300/1k)*20 = 150,060.
        assertThat(result.estimatedAmountKrw()).isEqualTo(150_060L);
        // directionalAmountKrw 는 방향 단가(입력 10원·출력 30원/1k):
        //   ceil(1,000,000/1k)*10 + ceil(502,300/1k)*30 = 10,000 + 15,090 = 25,090.
        assertThat(result.directionalAmountKrw()).isEqualTo(25_090L);
        assertThat(result.quotaStatus()).isEqualTo(QuotaStatus.OVER);
        assertThat(result.departments()).hasSize(2);
        assertThat(result.departments().get(0).teamId()).isEqualTo(10L);
        assertThat(result.departments().get(0).usedTokens()).isEqualTo(1_000L);
        // 부서 금액도 방향 차등: 입력 1,000·출력 0 → ceil(1,000/1k)*10 = 10.
        assertThat(result.departments().get(0).estimatedAmountKrw()).isEqualTo(10L);
    }

    @Test
    void dashboardFailsWhenPlanIsMissing() {
        when(companyTokenPlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCompanyDashboard(owner(), "2026-08"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MeteringErrorCode.MT_PLAN_NOT_FOUND));
    }

    @Test
    void invalidPeriodThrowsBusinessException() {
        assertThatThrownBy(() -> service.getCompanyDashboard(owner(), "2026-8"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MeteringErrorCode.MT_PERIOD_INVALID));
    }

    // overage 총량 단가 20, 방향 단가 입력 10·출력 30 — 방향 차등이 총량과 다른 금액을 내는지 본다.
    private static CompanyTokenPlan plan() {
        return CompanyTokenPlan.restore(1L, COMPANY, "STANDARD", 1_500_000L, 150_000, 20, 10, 30,
                LocalDate.of(2026, 1, 1));
    }

    private static AuthPrincipal owner() {
        return new AuthPrincipal(1L, COMPANY, "OWNER", false, 10L);
    }

    private static AuthPrincipal otherCompanyOwner() {
        return new AuthPrincipal(9L, OTHER_COMPANY, "OWNER", false, 10L);
    }

    private static AuthPrincipal otherCompanyLeader() {
        return new AuthPrincipal(9L, OTHER_COMPANY, "LEADER", false, 99L);
    }

    // ── 교차 회사 격리 ────────────────────────────────────────────────────
    // 대시보드는 이 도메인에서 실제 사용량을 읽는 유일한 경로다. 인가가 컨트롤러
    // @PreAuthorize 가 아니라 서비스에 있어서(Gate 1 AUTHZ_001), 회사 경계도 여기서
    // 단언하지 않으면 아무 데서도 안 본다.
    //
    // 집계 쿼리에 넘기는 companyId 가 principal 것 하나뿐인지를 본다 — 다른 회사 id 로
    // 한 번이라도 부르면 그 회사의 토큰 사용량이 응답에 섞인다.

    @Test
    void companyDashboardAggregatesOnlyPrincipalCompany() {
        when(companyTokenPlanRepository.findByCompanyId(OTHER_COMPANY)).thenReturn(Optional.of(plan()));
        when(tokenUsageRecordRepository.sumTotalTokens(OTHER_COMPANY, AUGUST_START, SEPTEMBER_START))
                .thenReturn(1_000L);
        // 방향 합계도 집계 경로다 — 안 채우면 서비스가 null 을 받는다(입력 600 · 출력 400, 합 = 총량).
        when(tokenUsageRecordRepository.sumDirectionTokens(OTHER_COMPANY, AUGUST_START, SEPTEMBER_START))
                .thenReturn(new TokenUsageRecordRepository.DirectionUsageAggregate(600L, 400L));
        when(tokenUsageRecordRepository.sumTotalTokensByDepartment(OTHER_COMPANY, AUGUST_START, SEPTEMBER_START))
                .thenReturn(List.of());

        MeteringDashboardResult result = service.getCompanyDashboard(otherCompanyOwner(), "2026-08");

        assertThat(result.usedTokens()).isEqualTo(1_000L);
        // 남의 회사 사용량은 한 번도 집계하지 않는다.
        verify(tokenUsageRecordRepository, never())
                .sumTotalTokens(eq(COMPANY), any(), any());
        verify(tokenUsageRecordRepository, never())
                .sumDirectionTokens(eq(COMPANY), any(), any());
        verify(tokenUsageRecordRepository, never())
                .sumTotalTokensByDepartment(eq(COMPANY), any(), any());
        verify(companyTokenPlanRepository, never()).findByCompanyId(COMPANY);
    }

    @Test
    void teamDashboardAggregatesOnlyPrincipalCompanyAndTeam() {
        when(companyTokenPlanRepository.findByCompanyId(OTHER_COMPANY)).thenReturn(Optional.of(plan()));
        when(tokenUsageRecordRepository.sumTotalTokensByTeam(OTHER_COMPANY, 99L, AUGUST_START, SEPTEMBER_START))
                .thenReturn(500L);

        TeamMeteringDashboardResult result = service.getMyTeamDashboard(otherCompanyLeader(), "2026-08");

        assertThat(result.usedTokens()).isEqualTo(500L);
        // 팀 id 를 그대로 두고 회사만 바꿔 부르는 일이 없어야 한다 — 팀 id 는 회사 간에 겹칠 수 있다.
        verify(tokenUsageRecordRepository, never())
                .sumTotalTokensByTeam(eq(COMPANY), any(), any(), any());
    }
}
