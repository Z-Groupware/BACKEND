package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.AnalysisLayerArtifactJpaEntity;

public interface SpringDataAnalysisLayerArtifactRepository
        extends JpaRepository<AnalysisLayerArtifactJpaEntity, Long> {

    /* UNIQUE(meeting_id, layer) 위의 조회다 — 회의당 계층당 1건이라 Optional 이 맞다. */
    Optional<AnalysisLayerArtifactJpaEntity> findByMeetingIdAndLayer(Long meetingId, String layer);
}
