package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.TranscriptChunkJpaEntity;

public interface SpringDataTranscriptChunkRepository extends JpaRepository<TranscriptChunkJpaEntity, Long> {

    /*
     * 정본 조회 정렬 규약.
     *
     * offset_ms 는 NULL 을 허용한다. MySQL 에서 NULL 은 오름차순 정렬의 **맨 앞**으로 가는데,
     * 그 발화들이 회의 앞부분이라는 보장이 없다. 오프셋이 없는 발화가 앞머리에 붙으면 L2 에
     * 넘기는 정본의 시작이 엉키고 주제 분할 기준선이 통째로 어긋난다.
     *
     * 그래서 NULL 을 **맨 뒤로** 보낸다 — 위치를 아는 발화가 먼저 시간순으로 오고, 모르는 것은
     * 뒤에 붙는다. 앞부분이 정확한 쪽이 낫다.
     *
     * seq 는 2차 정렬이다. 오프셋이 같거나 비어 있어도 적재 순서가 유지된다.
     *
     * 정렬을 Sort 로 넘기는 이유: NULLS LAST 는 메서드 이름으로 표현할 수 없고, JPQL 로 쓰면
     * QUERY_002(신규 @Query 금지)에 걸린다. Sort.Order#nullsLast 가 같은 결과를 규칙 안에서 준다.
     */
    Sort ORDER = Sort.by(
            Sort.Order.asc("offsetMs").nullsLast(),
            Sort.Order.asc("seq"));

    List<TranscriptChunkJpaEntity> findByMeetingId(Long meetingId, Sort sort);

    /*
     * L1 판정을 이식할 발화를 가져온다.
     *
     * meetingId 를 조건에 **함께** 넣는다. 판정 결과가 실어 온 id 만으로 갱신하면 다른 회의
     * (다른 회사)의 정본에 화자를 심을 수 있다 — 갱신은 성공하므로 아무도 오류를 못 본다.
     * 파생 쿼리로 두는 것은 신규 @Query 금지(QUERY_002) 때문이다.
     */
    List<TranscriptChunkJpaEntity> findByMeetingIdAndIdIn(Long meetingId, Collection<Long> ids);
}
