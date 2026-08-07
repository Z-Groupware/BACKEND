package com.module06.backend.capture.infrastructure.persistence.adapter;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.TupleVectorRepository;
import com.module06.backend.capture.infrastructure.persistence.entity.MeetingTupleVectorJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataMeetingTupleVectorRepository;

/*
 * few-shot 예시를 예약하는 어댑터다.
 *
 * Qdrant 를 부르지 않는다. **MySQL 이 원본이고 Qdrant 는 인덱스다** — 여기에 먼저 커밋하고
 * 임베딩은 AI-08 이 나중에 한다. 실패하면 vector_synced=false 로 남아 재시도 워커가 처리하고,
 * 라벨은 이미 안전하다. 순서를 뒤집으면 벡터는 검색에 걸리는데 꺼낼 내용이 없는 상태가 된다.
 */
@Repository
@RequiredArgsConstructor
public class MeetingTupleVectorPersistenceAdapter implements TupleVectorRepository {

    private final SpringDataMeetingTupleVectorRepository tupleVectorRepository;

    @Override
    @Transactional
    public void enqueue(VectorEntry entry) {
        tupleVectorRepository.save(MeetingTupleVectorJpaEntity.queued(
                entry.companyId(),
                entry.meetingId(),
                entry.layer().wireValue(),
                entry.inputText(),
                entry.payload(),
                entry.reviewLogId()));
    }
}
