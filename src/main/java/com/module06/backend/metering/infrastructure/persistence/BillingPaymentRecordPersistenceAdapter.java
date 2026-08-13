package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.BillingPaymentRecord;
import com.module06.backend.metering.domain.repository.BillingPaymentRecordRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BillingPaymentRecordPersistenceAdapter implements BillingPaymentRecordRepository {

    private final SpringDataBillingPaymentRecordRepository repository;

    public BillingPaymentRecordPersistenceAdapter(SpringDataBillingPaymentRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<BillingPaymentRecord> findByCompanyId(Long companyId) {
        return repository.findByCompanyIdOrderByBilledOnDescIdDesc(companyId).stream()
                .map(BillingPaymentRecordJpaEntity::toDomain)
                .toList();
    }

    @Override
    public BillingPaymentRecord save(BillingPaymentRecord paymentRecord) {
        return repository.save(BillingPaymentRecordJpaEntity.from(paymentRecord)).toDomain();
    }
}
