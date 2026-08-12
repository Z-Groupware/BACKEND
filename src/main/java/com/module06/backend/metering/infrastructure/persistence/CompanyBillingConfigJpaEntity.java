package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.CompanyBillingConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_billing_config")
public class CompanyBillingConfigJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "vat_included", nullable = false)
    private boolean vatIncluded;

    @Column(name = "storage_overage_price_per_gb", nullable = false)
    private int storageOveragePricePerGb;

    protected CompanyBillingConfigJpaEntity() {
    }

    private CompanyBillingConfigJpaEntity(Long id, Long companyId, boolean vatIncluded,
                                          int storageOveragePricePerGb) {
        this.id = id;
        this.companyId = companyId;
        this.vatIncluded = vatIncluded;
        this.storageOveragePricePerGb = storageOveragePricePerGb;
    }

    static CompanyBillingConfigJpaEntity from(CompanyBillingConfig config) {
        return new CompanyBillingConfigJpaEntity(
                config.getId(),
                config.getCompanyId(),
                config.isVatIncluded(),
                config.getStorageOveragePricePerGb()
        );
    }

    CompanyBillingConfig toDomain() {
        return CompanyBillingConfig.restore(id, companyId, vatIncluded, storageOveragePricePerGb);
    }
}
