package com.module06.backend.cap.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// recording 테이블 Spring Data JPA 리포지토리.
// ⚠️ Cap 접두어: 다른 도메인 동명 리포지토리와 빈 이름이 겹치는 것을 피한다.
public interface SpringDataCapRecordingRepository extends JpaRepository<CapRecordingJpaEntity, Long> {

    // 이 회의에 녹음본이 이미 등록됐는지 — 수동 업로드 중복 제출 판정용(파생 쿼리, QUERY_002 준수).
    boolean existsByMeetingId(Long meetingId);

    // 이 회의의 녹음본 조회 — UNIQUE(meeting_id)라 최대 1건(파생 쿼리, QUERY_002 준수).
    Optional<CapRecordingJpaEntity> findByMeetingId(Long meetingId);

    // 이 회의의 녹음본 삭제(하드 삭제) — 파생 삭제 쿼리(QUERY_002 준수). 트랜잭션 안에서 호출.
    void deleteByMeetingId(Long meetingId);
}
