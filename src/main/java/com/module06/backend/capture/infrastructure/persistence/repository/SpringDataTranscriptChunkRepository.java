package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.TranscriptChunkJpaEntity;

public interface SpringDataTranscriptChunkRepository extends JpaRepository<TranscriptChunkJpaEntity, Long> {

    /*
     * offset_ms 는 NULL 을 허용한다. MySQL 에서 NULL 은 오름차순 정렬의 맨 앞으로 가는데,
     * 그 발화들이 회의 앞부분이라는 보장이 없다. seq 를 2차 정렬로 두면 오프셋이 비어 있어도
     * 적재 순서가 유지된다 — L2 가 자를 기준선이 뒤섞이면 주제 분할이 통째로 어긋난다.
     */
    List<TranscriptChunkJpaEntity> findByMeetingIdOrderByOffsetMsAscSeqAsc(Long meetingId);
}
