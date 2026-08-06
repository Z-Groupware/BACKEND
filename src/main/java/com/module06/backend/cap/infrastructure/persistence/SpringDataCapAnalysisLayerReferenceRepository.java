package com.module06.backend.cap.infrastructure.persistence;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

// analysis_layer 읽기 전용 리포지토리. ⚠️ Cap 접두어로 빈 이름 충돌 회피.
public interface SpringDataCapAnalysisLayerReferenceRepository
        extends JpaRepository<CapAnalysisLayerReferenceEntity, Long> {

    // 이 회의에 아직 안 끝난 분석 계층(PENDING/RUNNING/FAILED)이 하나라도 있는지 — SKIPPED/DONE은 완료로 본다
    // (capture ProcessingStatus 판정과 동일: FAILED·RUNNING·PENDING이면 미완료). 파생 쿼리(QUERY_002 준수).
    boolean existsByMeetingIdAndStatusIn(Long meetingId, Collection<String> statuses);
}
