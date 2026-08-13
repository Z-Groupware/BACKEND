package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.BillingPaymentMethod;

import java.util.Optional;

public interface BillingPaymentMethodRepository {

    Optional<BillingPaymentMethod> findByCompanyId(Long companyId);

    BillingPaymentMethod save(BillingPaymentMethod paymentMethod);
}
