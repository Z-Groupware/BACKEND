package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.module06.backend.capture.infrastructure.persistence.entity.TranscriptChunkJpaEntity;

public interface SpringDataTranscriptChunkRepository extends JpaRepository<TranscriptChunkJpaEntity, Long> {

    /*
     * offset_ms 는 NULL 을 허용한다. MySQL 에서 NULL 은 오름차순 정렬의 **맨 앞**으로 가는데,
     * 그 발화들이 회의 앞부분이라는 보장이 없다. 오프셋이 없는 발화가 앞에 붙으면 L2 에
     * 넘기는 정본의 앞머리가 통째로 엉키고, 주제 분할 기준선이 어긋난다.
     *
     * 그래서 NULL 을 **맨 뒤로** 보낸다. 위치를 아는 발화가 먼저 시간순으로 오고, 모르는 것은
     * 뒤에 붙는다 — 앞부분이 정확한 쪽이 낫다. 파생 표현식 정렬은 메서드 이름으로 만들 수
     * 없어서 JPQL 로 쓴다(`CASE WHEN ... IS NULL` = NULLS LAST).
     *
     * seq 는 2차 정렬이다. 오프셋이 같거나 비어 있어도 적재 순서가 유지된다.
     */
    @Query("""
            select t from TranscriptChunkJpaEntity t
             where t.meetingId = :meetingId
             order by case when t.offsetMs is null then 1 else 0 end asc,
                      t.offsetMs asc,
                      t.seq asc
            """)
    List<TranscriptChunkJpaEntity> findByMeetingOrderedByOffset(@Param("meetingId") Long meetingId);
}
