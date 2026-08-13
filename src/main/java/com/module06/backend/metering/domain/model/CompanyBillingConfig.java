package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.BillingErrorCode;

import java.util.Objects;

public class CompanyBillingConfig {

    private final Long id;
    private final Long companyId;
    private final boolean vatIncluded;
    private final int storageOveragePricePerGb;

    private CompanyBillingConfig(Long id, Long companyId, boolean vatIncluded, int storageOveragePricePerGb) {
        this.id = id;
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        if (storageOveragePricePerGb < 0) {
            throw new BusinessException(BillingErrorCode.BIL_BILLING_CONFIG_COMMAND_INVALID);
        }
        this.vatIncluded = vatIncluded;
        this.storageOveragePricePerGb = storageOveragePricePerGb;
    }

    public static CompanyBillingConfig create(Long companyId, boolean vatIncluded, int storageOveragePricePerGb) {
        return new CompanyBillingConfig(null, companyId, vatIncluded, storageOveragePricePerGb);
    }

    public static CompanyBillingConfig restore(Long id, Long companyId, boolean vatIncluded,
                                               int storageOveragePricePerGb) {
        return new CompanyBillingConfig(id, companyId, vatIncluded, storageOveragePricePerGb);
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public boolean isVatIncluded() {
        return vatIncluded;
    }

    public int getStorageOveragePricePerGb() {
        return storageOveragePricePerGb;
    }
}
