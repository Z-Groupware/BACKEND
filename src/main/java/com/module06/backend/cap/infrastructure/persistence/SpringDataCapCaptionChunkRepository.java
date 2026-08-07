package com.module06.backend.cap.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// caption_chunk 쓰기 리포지토리. ⚠️ Cap 접두어로 빈 이름 충돌 회피(팀 컨벤션).
public interface SpringDataCapCaptionChunkRepository extends JpaRepository<CapCaptionChunkJpaEntity, Long> {

    // 재전송(중복) 판정 — UNIQUE(meeting_id, member_id, seq)와 동일 키(파생 쿼리, QUERY_002 준수).
    boolean existsByMeetingIdAndMemberIdAndSeq(Long meetingId, Long memberId, int seq);
}
