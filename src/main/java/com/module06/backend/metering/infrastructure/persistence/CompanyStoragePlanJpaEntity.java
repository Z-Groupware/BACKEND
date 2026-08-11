package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_storage_plan")
public class CompanyStoragePlanJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "storage_cap_bytes", nullable = false)
    private long storageCapBytes;

    protected CompanyStoragePlanJpaEntity() {
    }

    private CompanyStoragePlanJpaEntity(Long id, Long companyId, long storageCapBytes) {
        this.id = id;
        this.companyId = companyId;
        this.storageCapBytes = storageCapBytes;
    }

    static CompanyStoragePlanJpaEntity from(CompanyStoragePlan plan) {
        return new CompanyStoragePlanJpaEntity(plan.getId(), plan.getCompanyId(), plan.getStorageCapBytes());
    }

    CompanyStoragePlan toDomain() {
        return CompanyStoragePlan.restore(id, companyId, storageCapBytes);
    }
}
