package com.module06.backend.metering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataBillingPaymentRecordRepository extends JpaRepository<BillingPaymentRecordJpaEntity, Long> {

    List<BillingPaymentRecordJpaEntity> findByCompanyIdOrderByBilledOnDescIdDesc(Long companyId);
}
