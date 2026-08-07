package com.module06.backend.metering.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public class CompanyTokenPlan {

    private final Long id;
    private final Long companyId;
    private final String planCode;
    private final long monthlyTokenPool;
    private final int baseFee;
    private final int tokenOveragePricePer1k;
    private final LocalDate effectiveFrom;

    private CompanyTokenPlan(Long id, Long companyId, String planCode, long monthlyTokenPool, int baseFee,
                             int tokenOveragePricePer1k, LocalDate effectiveFrom) {
        this.id = id;
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.planCode = Objects.requireNonNull(planCode, "planCode must not be null");
        this.monthlyTokenPool = monthlyTokenPool;
        this.baseFee = baseFee;
        this.tokenOveragePricePer1k = tokenOveragePricePer1k;
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    }

    public static CompanyTokenPlan create(Long companyId, String planCode, long monthlyTokenPool, int baseFee,
                                          int tokenOveragePricePer1k, LocalDate effectiveFrom) {
        return new CompanyTokenPlan(null, companyId, planCode, monthlyTokenPool, baseFee,
                tokenOveragePricePer1k, effectiveFrom);
    }

    public static CompanyTokenPlan restore(Long id, Long companyId, String planCode, long monthlyTokenPool, int baseFee,
                                           int tokenOveragePricePer1k, LocalDate effectiveFrom) {
        return new CompanyTokenPlan(id, companyId, planCode, monthlyTokenPool, baseFee,
                tokenOveragePricePer1k, effectiveFrom);
    }

    public long overageTokens(long usedTokens) {
        return Math.max(0L, usedTokens - monthlyTokenPool);
    }

    public long estimatedAmountKrw(long usedTokens) {
        long overage = overageTokens(usedTokens);
        long overageUnits = (overage + 999L) / 1000L;
        return baseFee + overageUnits * tokenOveragePricePer1k;
    }

    public long usageAmountKrw(long usedTokens) {
        long units = (usedTokens + 999L) / 1000L;
        return units * tokenOveragePricePer1k;
    }

    public QuotaStatus quotaStatus(long usedTokens) {
        if (usedTokens < monthlyTokenPool * 0.8d) {
            return QuotaStatus.WITHIN;
        }
        if (usedTokens < monthlyTokenPool) {
            return QuotaStatus.SOFT_WARN;
        }
        return QuotaStatus.OVER;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public long getMonthlyTokenPool() {
        return monthlyTokenPool;
    }

    public int getBaseFee() {
        return baseFee;
    }

    public int getTokenOveragePricePer1k() {
        return tokenOveragePricePer1k;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }
}
