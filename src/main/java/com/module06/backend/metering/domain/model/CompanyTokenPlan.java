package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

import java.time.LocalDate;
import java.util.Objects;

public class CompanyTokenPlan {

    private final Long id;
    private final Long companyId;
    private final String planCode;
    private final long monthlyTokenPool;
    private final int baseFee;
    private final int tokenOveragePricePer1k;
    // 방향별 단가. LLM 은 입력·출력 원가가 달라, 총량 단가 하나로는 실제 비용 구조가
    // 청구서에 드러나지 않는다. 할당량/예상요금은 여전히 총량(tokenOveragePricePer1k)으로
    // 판정하고, "실사용 금액"만 이 두 단가로 방향을 나눠 계산한다.
    private final int inputTokenPricePer1k;
    private final int outputTokenPricePer1k;
    private final LocalDate effectiveFrom;

    private CompanyTokenPlan(Long id, Long companyId, String planCode, long monthlyTokenPool, int baseFee,
                             int tokenOveragePricePer1k, int inputTokenPricePer1k, int outputTokenPricePer1k,
                             LocalDate effectiveFrom) {
        this.id = id;
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.planCode = Objects.requireNonNull(planCode, "planCode must not be null");
        // create·restore 공통 방어선. 음수 풀·기본료·단가는 음수 예상 금액과
        // 잘못된 할당량 상태를 만든다 — 커맨드(입력) 뿐 아니라 도메인에서도 막는다.
        if (monthlyTokenPool < 0 || baseFee < 0 || tokenOveragePricePer1k < 0
                || inputTokenPricePer1k < 0 || outputTokenPricePer1k < 0) {
            throw new BusinessException(MeteringErrorCode.MT_PLAN_COMMAND_INVALID);
        }
        this.monthlyTokenPool = monthlyTokenPool;
        this.baseFee = baseFee;
        this.tokenOveragePricePer1k = tokenOveragePricePer1k;
        this.inputTokenPricePer1k = inputTokenPricePer1k;
        this.outputTokenPricePer1k = outputTokenPricePer1k;
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    }

    public static CompanyTokenPlan create(Long companyId, String planCode, long monthlyTokenPool, int baseFee,
                                          int tokenOveragePricePer1k, int inputTokenPricePer1k,
                                          int outputTokenPricePer1k, LocalDate effectiveFrom) {
        return new CompanyTokenPlan(null, companyId, planCode, monthlyTokenPool, baseFee,
                tokenOveragePricePer1k, inputTokenPricePer1k, outputTokenPricePer1k, effectiveFrom);
    }

    public static CompanyTokenPlan restore(Long id, Long companyId, String planCode, long monthlyTokenPool, int baseFee,
                                           int tokenOveragePricePer1k, int inputTokenPricePer1k,
                                           int outputTokenPricePer1k, LocalDate effectiveFrom) {
        return new CompanyTokenPlan(id, companyId, planCode, monthlyTokenPool, baseFee,
                tokenOveragePricePer1k, inputTokenPricePer1k, outputTokenPricePer1k, effectiveFrom);
    }

    public long overageTokens(long usedTokens) {
        return Math.max(0L, usedTokens - monthlyTokenPool);
    }

    public long estimatedAmountKrw(long usedTokens) {
        long overage = overageTokens(usedTokens);
        long overageUnits = (overage + 999L) / 1000L;
        return baseFee + overageUnits * tokenOveragePricePer1k;
    }

    /*
     * 총량 단일 단가 기준 실사용 금액. 방향 구분 없이 전체 토큰에 한 단가를 곱한다.
     * 방향별 단가와 비교하기 위한 기준선(baseline)으로 남긴다.
     */
    public long usageAmountKrw(long usedTokens) {
        long units = (usedTokens + 999L) / 1000L;
        return units * tokenOveragePricePer1k;
    }

    /*
     * 방향별 단가 기준 실사용 금액. 입력·출력을 각각 1k 단위로 올림해 서로 다른 단가를 곱한다.
     * 입력 단가와 출력 단가가 같으면 반올림 단위가 방향별로 나뉘는 만큼만 총량 계산과 달라진다 —
     * 그 차이가 "실제 비용 구조가 청구에 반영된다"는 뜻이다.
     */
    public long usageAmountKrw(long inputTokens, long outputTokens) {
        long inputUnits = (Math.max(0L, inputTokens) + 999L) / 1000L;
        long outputUnits = (Math.max(0L, outputTokens) + 999L) / 1000L;
        return inputUnits * inputTokenPricePer1k + outputUnits * outputTokenPricePer1k;
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

    public int getInputTokenPricePer1k() {
        return inputTokenPricePer1k;
    }

    public int getOutputTokenPricePer1k() {
        return outputTokenPricePer1k;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }
}
