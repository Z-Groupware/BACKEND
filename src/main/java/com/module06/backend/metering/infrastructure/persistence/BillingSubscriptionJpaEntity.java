package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.BillingSubscription;
import com.module06.backend.metering.domain.model.BillingSubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "subscription")
public class BillingSubscriptionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "plan", nullable = false)
    private String planCode;

    @Column(name = "seats", nullable = false)
    private int seats;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillingSubscriptionStatus status;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "current_period_start", nullable = false)
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDate currentPeriodEnd;

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "carried_overage_amount", nullable = false)
    private int carriedOverageAmount;

    protected BillingSubscriptionJpaEntity() {
    }

    private BillingSubscriptionJpaEntity(Long id, Long companyId, String planCode, int seats,
                                         BillingSubscriptionStatus status, LocalDate startedOn,
                                         LocalDate currentPeriodStart, LocalDate currentPeriodEnd,
                                         LocalDate nextBillingDate, int carriedOverageAmount) {
        this.id = id;
        this.companyId = companyId;
        this.planCode = planCode;
        this.seats = seats;
        this.status = status;
        this.startedOn = startedOn;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.nextBillingDate = nextBillingDate;
        this.carriedOverageAmount = carriedOverageAmount;
    }

    static BillingSubscriptionJpaEntity from(BillingSubscription subscription) {
        return new BillingSubscriptionJpaEntity(
                subscription.getId(),
                subscription.getCompanyId(),
                subscription.getPlanCode(),
                subscription.getSeats(),
                subscription.getStatus(),
                subscription.getStartedOn(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getNextBillingDate(),
                subscription.getCarriedOverageAmount()
        );
    }

    BillingSubscription toDomain() {
        return BillingSubscription.restore(id, companyId, planCode, seats, status, startedOn,
                currentPeriodStart, currentPeriodEnd, nextBillingDate, carriedOverageAmount);
    }
}
