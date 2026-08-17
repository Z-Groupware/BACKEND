package com.module06.backend.cap.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
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

    // 회사 무관이 의도다 — TENANT_001 예외(#574).
    //
    // 호출 경로가 LostSttTriggerRepositoryAdapter 하나뿐이고, 그 위는 사용자 요청이 아니라 주기 배치
    // (LostSttTriggerRecoveryService)다. 요청자가 없으므로 좁힐 companyId 자체가 존재하지 않는다 —
    // 유실된 트리거는 회사를 가리지 않고 일어나므로 전 회사를 봐야 한다.
    //
    // 결과가 응답으로 나가지 않는다. 재트리거에만 쓰이고, 재트리거는 그 녹음이 원래 속한 회의로만
    // 향한다(Recording.meetingId). 다른 회사의 행을 읽어도 그 데이터가 남에게 보일 경로가 없다.
    //
    // 파생 쿼리라 QUERY_002 도 준수한다.
    // nosemgrep: tenant-derived-query-without-company-scope
    List<CapRecordingJpaEntity> findBySttTriggeredTrueAndCreatedAtBetweenOrderByCreatedAtAsc(
            LocalDateTime createdFrom, LocalDateTime createdUntil, Pageable pageable);
}
