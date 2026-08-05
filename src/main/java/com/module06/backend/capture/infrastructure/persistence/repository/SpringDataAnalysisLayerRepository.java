package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.AnalysisLayerJpaEntity;

public interface SpringDataAnalysisLayerRepository extends JpaRepository<AnalysisLayerJpaEntity, Long> {

    Optional<AnalysisLayerJpaEntity> findByMeetingIdAndLayer(Long meetingId, String layer);

    List<AnalysisLayerJpaEntity> findByMeetingIdOrderByIdAsc(Long meetingId);
}
