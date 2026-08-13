package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.BillingPaymentRecord;

import java.util.List;

public interface BillingPaymentRecordRepository {

    List<BillingPaymentRecord> findByCompanyId(Long companyId);

    BillingPaymentRecord save(BillingPaymentRecord paymentRecord);
}
