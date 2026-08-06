package com.module06.backend.cap.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// stt_block 읽기 전용 리포지토리. ⚠️ Cap 접두어로 빈 이름 충돌 회피.
public interface SpringDataCapSttBlockReferenceRepository extends JpaRepository<CapSttBlockReferenceEntity, Long> {

    // 이 회의에 DONE이 아닌 STT 블록이 하나라도 있는지 — "모든 블록 완료" 판정(파생 쿼리, QUERY_002 준수).
    boolean existsByMeetingIdAndStatusNot(Long meetingId, String status);

    // 이 회의에 STT 블록이 하나라도 생겼는지 — 트리거는 됐는데 아직 0건인 "진행 중" 구간을 가려내는 데 쓴다.
    boolean existsByMeetingId(Long meetingId);
}
