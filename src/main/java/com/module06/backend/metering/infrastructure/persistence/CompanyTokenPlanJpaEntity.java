package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "company_token_plan")
public class CompanyTokenPlanJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "monthly_token_pool", nullable = false)
    private long monthlyTokenPool;

    @Column(name = "base_fee", nullable = false)
    private int baseFee;

    @Column(name = "token_overage_price_per_1k", nullable = false)
    private int tokenOveragePricePer1k;

    @Column(name = "input_token_price_per_1k", nullable = false)
    private int inputTokenPricePer1k;

    @Column(name = "output_token_price_per_1k", nullable = false)
    private int outputTokenPricePer1k;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    protected CompanyTokenPlanJpaEntity() {
    }

    private CompanyTokenPlanJpaEntity(Long id, Long companyId, String planCode, long monthlyTokenPool,
                                      int baseFee, int tokenOveragePricePer1k, int inputTokenPricePer1k,
                                      int outputTokenPricePer1k, LocalDate effectiveFrom) {
        this.id = id;
        this.companyId = companyId;
        this.planCode = planCode;
        this.monthlyTokenPool = monthlyTokenPool;
        this.baseFee = baseFee;
        this.tokenOveragePricePer1k = tokenOveragePricePer1k;
        this.inputTokenPricePer1k = inputTokenPricePer1k;
        this.outputTokenPricePer1k = outputTokenPricePer1k;
        this.effectiveFrom = effectiveFrom;
    }

    static CompanyTokenPlanJpaEntity from(CompanyTokenPlan plan) {
        return new CompanyTokenPlanJpaEntity(
                plan.getId(),
                plan.getCompanyId(),
                plan.getPlanCode(),
                plan.getMonthlyTokenPool(),
                plan.getBaseFee(),
                plan.getTokenOveragePricePer1k(),
                plan.getInputTokenPricePer1k(),
                plan.getOutputTokenPricePer1k(),
                plan.getEffectiveFrom()
        );
    }

    CompanyTokenPlan toDomain() {
        return CompanyTokenPlan.restore(id, companyId, planCode, monthlyTokenPool, baseFee,
                tokenOveragePricePer1k, inputTokenPricePer1k, outputTokenPricePer1k, effectiveFrom);
    }
}
