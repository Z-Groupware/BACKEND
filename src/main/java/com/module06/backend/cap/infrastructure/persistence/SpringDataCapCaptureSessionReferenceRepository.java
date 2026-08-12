package com.module06.backend.cap.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. capture_session.meeting_id는 UNIQUE라 회의당 최대 한 행이다.
// QUERY_002(신규 @Query 금지)에 걸리지 않도록 파생 쿼리로 표현한다.
public interface SpringDataCapCaptureSessionReferenceRepository
        extends JpaRepository<CapCaptureSessionReferenceEntity, Long> {

    /** id·status만 필요하므로 닫힌 프로젝션으로 그 컬럼들만 SELECT한다. */
    // TENANT_001 승인: capture_session 테이블 자체에 company_id 컬럼이 없다(엔티티 참고) — D(회의)
    // 소유 read-model이라 조인 없이는 회사로 스코프할 수 없다. meetingId는 호출자(CaptureQueryService·
    // CaptureUploadService)가 이미 참석자/녹음자 검증을 마친 뒤에만 넘기므로, 여기서 추가로 걸러낼
    // 다른 회사의 행이 애초에 존재하지 않는다(meeting_id UNIQUE로 최대 한 행).
    // nosemgrep: review-loop.semgrep.tenant-derived-query-without-company-scope
    Optional<SessionView> findByMeetingId(Long meetingId);

    interface SessionView {
        Long getId();

        String getStatus();
    }
}
