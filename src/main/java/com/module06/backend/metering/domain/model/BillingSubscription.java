package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.BillingErrorCode;

import java.time.LocalDate;
import java.util.Objects;

public class BillingSubscription {

    private final Long id;
    private final Long companyId;
    private final String planCode;
    private final int seats;
    private final BillingSubscriptionStatus status;
    private final LocalDate startedOn;
    private final LocalDate currentPeriodStart;
    private final LocalDate currentPeriodEnd;
    private final LocalDate nextBillingDate;
    private final int carriedOverageAmount;

    private BillingSubscription(Long id, Long companyId, String planCode, int seats,
                                BillingSubscriptionStatus status, LocalDate startedOn,
                                LocalDate currentPeriodStart, LocalDate currentPeriodEnd,
                                LocalDate nextBillingDate, int carriedOverageAmount) {
        this.id = id;
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.planCode = Objects.requireNonNull(planCode, "planCode must not be null");
        if (seats < 0 || carriedOverageAmount < 0) {
            throw new BusinessException(BillingErrorCode.BIL_SUBSCRIPTION_COMMAND_INVALID);
        }
        this.seats = seats;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startedOn = Objects.requireNonNull(startedOn, "startedOn must not be null");
        this.currentPeriodStart = Objects.requireNonNull(currentPeriodStart, "currentPeriodStart must not be null");
        if (currentPeriodEnd != null && currentPeriodEnd.isBefore(this.currentPeriodStart)) {
            throw new BusinessException(BillingErrorCode.BIL_SUBSCRIPTION_COMMAND_INVALID);
        }
        this.currentPeriodEnd = currentPeriodEnd;
        this.nextBillingDate = nextBillingDate;
        this.carriedOverageAmount = carriedOverageAmount;
    }

    public static BillingSubscription create(Long companyId, String planCode, int seats,
                                             BillingSubscriptionStatus status, LocalDate startedOn,
                                             LocalDate currentPeriodStart, LocalDate currentPeriodEnd,
                                             LocalDate nextBillingDate, int carriedOverageAmount) {
        return new BillingSubscription(null, companyId, planCode, seats, status, startedOn,
                currentPeriodStart, currentPeriodEnd, nextBillingDate, carriedOverageAmount);
    }

    public static BillingSubscription restore(Long id, Long companyId, String planCode, int seats,
                                              BillingSubscriptionStatus status, LocalDate startedOn,
                                              LocalDate currentPeriodStart, LocalDate currentPeriodEnd,
                                              LocalDate nextBillingDate, int carriedOverageAmount) {
        return new BillingSubscription(id, companyId, planCode, seats, status, startedOn,
                currentPeriodStart, currentPeriodEnd, nextBillingDate, carriedOverageAmount);
    }

    public BillingSubscription activate(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new BusinessException(BillingErrorCode.BIL_SUBSCRIPTION_COMMAND_INVALID);
        }
        return restore(id, companyId, planCode, seats, BillingSubscriptionStatus.ACTIVE, startedOn,
                periodStart, periodEnd, periodEnd, carriedOverageAmount);
    }

    public BillingSubscription cancelAtPeriodEnd() {
        if (status != BillingSubscriptionStatus.ACTIVE) {
            throw new BusinessException(BillingErrorCode.BIL_SUBSCRIPTION_COMMAND_INVALID);
        }
        return restore(id, companyId, planCode, seats, BillingSubscriptionStatus.CANCELING, startedOn,
                currentPeriodStart, currentPeriodEnd, nextBillingDate, carriedOverageAmount);
    }

    public BillingSubscription resume() {
        if (status != BillingSubscriptionStatus.CANCELING) {
            throw new BusinessException(BillingErrorCode.BIL_SUBSCRIPTION_COMMAND_INVALID);
        }
        return restore(id, companyId, planCode, seats, BillingSubscriptionStatus.ACTIVE, startedOn,
                currentPeriodStart, currentPeriodEnd, nextBillingDate, carriedOverageAmount);
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

    public int getSeats() {
        return seats;
    }

    public BillingSubscriptionStatus getStatus() {
        return status;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public LocalDate getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public LocalDate getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public LocalDate getNextBillingDate() {
        return nextBillingDate;
    }

    public int getCarriedOverageAmount() {
        return carriedOverageAmount;
    }
}
