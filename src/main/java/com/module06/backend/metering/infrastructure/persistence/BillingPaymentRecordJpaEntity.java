package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.BillingPaymentRecord;
import com.module06.backend.metering.domain.model.BillingPaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "billing_history")
public class BillingPaymentRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "billed_on", nullable = false)
    private LocalDate billedOn;

    @Column(name = "plan", nullable = false)
    private String planCode;

    @Column(name = "seats", nullable = false)
    private int seats;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "overage_amount", nullable = false)
    private BigDecimal overageAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillingPaymentStatus status;

    protected BillingPaymentRecordJpaEntity() {
    }

    private BillingPaymentRecordJpaEntity(Long id, Long companyId, LocalDate billedOn, String planCode, int seats,
                                          BigDecimal amount, BigDecimal overageAmount,
                                          BillingPaymentStatus status) {
        this.id = id;
        this.companyId = companyId;
        this.billedOn = billedOn;
        this.planCode = planCode;
        this.seats = seats;
        this.amount = amount;
        this.overageAmount = overageAmount;
        this.status = status;
    }

    static BillingPaymentRecordJpaEntity from(BillingPaymentRecord paymentRecord) {
        return new BillingPaymentRecordJpaEntity(
                paymentRecord.getId(),
                paymentRecord.getCompanyId(),
                paymentRecord.getBilledOn(),
                paymentRecord.getPlanCode(),
                paymentRecord.getSeats(),
                paymentRecord.getAmount(),
                paymentRecord.getOverageAmount(),
                paymentRecord.getStatus()
        );
    }

    BillingPaymentRecord toDomain() {
        return BillingPaymentRecord.restore(id, companyId, billedOn, planCode, seats, amount, overageAmount, status);
    }
}
