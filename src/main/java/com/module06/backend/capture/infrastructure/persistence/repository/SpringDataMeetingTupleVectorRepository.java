package com.module06.backend.capture.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingTupleVectorJpaEntity;

/*
 * meeting_tuple_vector 접근. 이 저장소는 **예약만** 한다 — 재시도 워커 조회(vector_synced=false)는
 * AI-08 쪽 관심사이고, 그 메서드를 여기 미리 두면 쓰지 않는 쿼리가 남는다.
 */
public interface SpringDataMeetingTupleVectorRepository extends JpaRepository<MeetingTupleVectorJpaEntity, Long> {
}
