package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.BillingPaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "payment_method")
public class BillingPaymentMethodJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "brand", nullable = false, length = 30)
    private String brand;

    @Column(name = "last4", nullable = false, length = 4)
    private String last4;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    // KNOWN GAP - plaintext OK only because PG is mocked; when Toss is wired, billing_key MUST be
    // envelope-encrypted (KMS) before persist and decrypted only at PG call.
    @Column(name = "billing_key")
    private String billingKey;

    @Column(name = "is_default", nullable = false)
    private boolean defaultMethod;

    protected BillingPaymentMethodJpaEntity() {
    }

    private BillingPaymentMethodJpaEntity(Long id, Long companyId, String brand, String last4,
                                          LocalDate expiresOn, String billingKey, boolean defaultMethod) {
        this.id = id;
        this.companyId = companyId;
        this.brand = brand;
        this.last4 = last4;
        this.expiresOn = expiresOn;
        this.billingKey = billingKey;
        this.defaultMethod = defaultMethod;
    }

    static BillingPaymentMethodJpaEntity from(BillingPaymentMethod paymentMethod) {
        return new BillingPaymentMethodJpaEntity(
                paymentMethod.getId(),
                paymentMethod.getCompanyId(),
                paymentMethod.getBrand(),
                paymentMethod.getLast4(),
                paymentMethod.getExpiresOn(),
                paymentMethod.getBillingKey(),
                paymentMethod.isDefaultMethod()
        );
    }

    BillingPaymentMethod toDomain() {
        return BillingPaymentMethod.restore(id, companyId, brand, last4, expiresOn, billingKey, defaultMethod);
    }
}
