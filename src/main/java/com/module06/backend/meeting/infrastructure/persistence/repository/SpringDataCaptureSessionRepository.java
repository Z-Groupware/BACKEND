package com.module06.backend.meeting.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.meeting.infrastructure.persistence.entity.CaptureSessionJpaEntity;

/*
 * capture_session 테이블의 저장과 회의별 존재 여부 조회를 수행하는 Spring Data 저장소다.
 */
public interface SpringDataCaptureSessionRepository extends JpaRepository<CaptureSessionJpaEntity, Long> {

    /* 서비스 사전 검증을 위해 해당 회의의 캡처 세션 존재 여부를 파생 쿼리로 확인한다. */
    boolean existsByMeetingId(Long meetingId);
}
