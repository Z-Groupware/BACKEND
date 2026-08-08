package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingTupleVectorJpaEntity;

/*
 * meeting_tuple_vector 접근.
 *
 * 예약(RVW-02)과 인덱스 반영(AI-08 워커)이 같은 표를 쓴다 — 원본이 하나이기 때문이다.
 */
public interface SpringDataMeetingTupleVectorRepository extends JpaRepository<MeetingTupleVectorJpaEntity, Long> {

    /*
     * 아직 못 올라간 예시를 오래된 것부터 가져온다.
     *
     * **파생 쿼리로 둔다.** 같은 조건을 @Query 로 적으면 Gate1(QUERY_002)에 걸리고, 무엇보다
     * 인덱스(IX_TUPLE_VECTOR_SYNC_RETRY = vector_synced, sync_attempts)와 조건 순서를 손으로
     * 맞춰야 한다 — 그 짝이 어긋나도 쿼리는 돌기 때문에 느려진 뒤에야 드러난다.
     *
     * id 오름차순인 이유 — 먼저 예약된 라벨이 먼저 올라가야 한다. 뒤엣것부터 올리면 실패가
     * 쌓였을 때 오래된 라벨이 계속 밀린다.
     */
    List<MeetingTupleVectorJpaEntity> findByVectorSyncedFalseAndSyncAttemptsLessThanOrderByIdAsc(
            int syncAttempts, Limit limit);
}
