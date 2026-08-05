package com.module06.backend.handover.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverInsightJpaRepository extends JpaRepository<HandoverInsightJpaEntity, Long> {

    void deleteByHandoverId(Long handoverId);
}
