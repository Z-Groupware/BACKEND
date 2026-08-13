package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.BillingErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class BillingPaymentRecord {

    private final Long id;
    private final Long companyId;
    private final LocalDate billedOn;
    private final String planCode;
    private final int seats;
    private final BigDecimal amount;
    private final BigDecimal overageAmount;
    private final BillingPaymentStatus status;

    private BillingPaymentRecord(Long id, Long companyId, LocalDate billedOn, String planCode, int seats,
                                 BigDecimal amount, BigDecimal overageAmount, BillingPaymentStatus status) {
        this.id = id;
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.billedOn = Objects.requireNonNull(billedOn, "billedOn must not be null");
        this.planCode = Objects.requireNonNull(planCode, "planCode must not be null");
        if (seats < 0 || isNegative(amount) || isNegative(overageAmount)) {
            throw new BusinessException(BillingErrorCode.BIL_PAYMENT_HISTORY_COMMAND_INVALID);
        }
        this.seats = seats;
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.overageAmount = Objects.requireNonNull(overageAmount, "overageAmount must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static BillingPaymentRecord create(Long companyId, LocalDate billedOn, String planCode, int seats,
                                              BigDecimal amount, BigDecimal overageAmount,
                                              BillingPaymentStatus status) {
        return new BillingPaymentRecord(null, companyId, billedOn, planCode, seats, amount, overageAmount, status);
    }

    public static BillingPaymentRecord restore(Long id, Long companyId, LocalDate billedOn, String planCode, int seats,
                                               BigDecimal amount, BigDecimal overageAmount,
                                               BillingPaymentStatus status) {
        return new BillingPaymentRecord(id, companyId, billedOn, planCode, seats, amount, overageAmount, status);
    }

    private static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public LocalDate getBilledOn() {
        return billedOn;
    }

    public String getPlanCode() {
        return planCode;
    }

    public int getSeats() {
        return seats;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getOverageAmount() {
        return overageAmount;
    }

    public BillingPaymentStatus getStatus() {
        return status;
    }
}
