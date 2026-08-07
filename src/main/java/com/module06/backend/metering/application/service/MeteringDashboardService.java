package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.DepartmentUsageResult;
import com.module06.backend.metering.application.result.MeteringDashboardResult;
import com.module06.backend.metering.application.result.TeamMeteringDashboardResult;
import com.module06.backend.metering.application.usecase.GetMeteringDashboardUseCase;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class MeteringDashboardService implements GetMeteringDashboardUseCase {

    private static final String OWNER_ROLE = "OWNER";
    private static final String LEADER_ROLE = "LEADER";

    private final TokenUsageRecordRepository tokenUsageRecordRepository;
    private final CompanyTokenPlanRepository companyTokenPlanRepository;

    public MeteringDashboardService(TokenUsageRecordRepository tokenUsageRecordRepository,
                                    CompanyTokenPlanRepository companyTokenPlanRepository) {
        this.tokenUsageRecordRepository = tokenUsageRecordRepository;
        this.companyTokenPlanRepository = companyTokenPlanRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MeteringDashboardResult getCompanyDashboard(AuthPrincipal principal, String period) {
        requireCompanyDashboardScope(principal);
        YearMonth yearMonth = parsePeriod(period);
        PeriodBounds bounds = bounds(yearMonth);
        CompanyTokenPlan plan = findPlan(principal.companyId());
        long usedTokens = tokenUsageRecordRepository.sumTotalTokens(principal.companyId(), bounds.start(), bounds.end());
        List<DepartmentUsageResult> departments = tokenUsageRecordRepository
                .sumTotalTokensByDepartment(principal.companyId(), bounds.start(), bounds.end())
                .stream()
                .map(usage -> new DepartmentUsageResult(
                        usage.teamId(),
                        usage.usedTokens(),
                        plan.usageAmountKrw(usage.usedTokens())))
                .toList();

        return new MeteringDashboardResult(
                yearMonth,
                usedTokens,
                plan.getMonthlyTokenPool(),
                plan.overageTokens(usedTokens),
                plan.estimatedAmountKrw(usedTokens),
                plan.quotaStatus(usedTokens),
                departments
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TeamMeteringDashboardResult getMyTeamDashboard(AuthPrincipal principal, String period) {
        requireTeamDashboardScope(principal);
        YearMonth yearMonth = parsePeriod(period);
        PeriodBounds bounds = bounds(yearMonth);
        CompanyTokenPlan plan = findPlan(principal.companyId());
        long usedTokens = tokenUsageRecordRepository.sumTotalTokensByTeam(
                principal.companyId(), principal.teamId(), bounds.start(), bounds.end());

        return new TeamMeteringDashboardResult(
                yearMonth,
                principal.teamId(),
                usedTokens,
                plan.getMonthlyTokenPool(),
                plan.overageTokens(usedTokens),
                plan.quotaStatus(usedTokens)
        );
    }

    private void requireCompanyDashboardScope(AuthPrincipal principal) {
        if (principal == null || principal.companyId() == null
                || (!principal.isAdmin() && !OWNER_ROLE.equals(principal.getAuthority()))) {
            throw new BusinessException(MeteringErrorCode.MT_FORBIDDEN_SCOPE);
        }
    }

    private void requireTeamDashboardScope(AuthPrincipal principal) {
        if (principal == null || principal.companyId() == null || principal.teamId() == null
                || !LEADER_ROLE.equals(principal.getAuthority())) {
            throw new BusinessException(MeteringErrorCode.MT_FORBIDDEN_SCOPE);
        }
    }

    private CompanyTokenPlan findPlan(Long companyId) {
        return companyTokenPlanRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BusinessException(MeteringErrorCode.MT_PLAN_NOT_FOUND));
    }

    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new BusinessException(MeteringErrorCode.MT_PERIOD_INVALID);
        }
    }

    private PeriodBounds bounds(YearMonth period) {
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();
        return new PeriodBounds(start, end);
    }

    private record PeriodBounds(LocalDateTime start, LocalDateTime end) {
    }
}
