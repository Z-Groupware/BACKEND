package com.module06.backend.cap.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. capture_session.meeting_id는 UNIQUE라 회의당 최대 한 행이다.
// QUERY_002(신규 @Query 금지)에 걸리지 않도록 파생 쿼리로 표현한다.
public interface SpringDataCapCaptureSessionReferenceRepository
        extends JpaRepository<CapCaptureSessionReferenceEntity, Long> {

    /** id·status만 필요하므로 닫힌 프로젝션으로 그 컬럼들만 SELECT한다. */
    Optional<SessionView> findByMeetingId(Long meetingId);

    interface SessionView {
        Long getId();

        String getStatus();
    }
}
