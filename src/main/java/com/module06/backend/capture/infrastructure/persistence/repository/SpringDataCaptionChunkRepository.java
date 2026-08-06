package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.CaptionChunkJpaEntity;

public interface SpringDataCaptionChunkRepository extends JpaRepository<CaptionChunkJpaEntity, Long> {

    /*
     * 시작 오프셋 순으로 읽는다. 판정 자체는 순서와 무관하지만(참석자별 최대 rms 를 모으므로),
     * 로그와 디버깅에서 시간순이 아니면 어느 구간이 문제인지 찾을 수 없다.
     *
     * start_offset_ms 는 NOT NULL 이라 transcript_chunk 처럼 nullsLast 가 필요 없다.
     * IX_CAPTION_CHUNK_MEETING_OFFSET(meeting_id, start_offset_ms) 를 그대로 탄다.
     */
    List<CaptionChunkJpaEntity> findByMeetingIdOrderByStartOffsetMsAsc(Long meetingId);
}
