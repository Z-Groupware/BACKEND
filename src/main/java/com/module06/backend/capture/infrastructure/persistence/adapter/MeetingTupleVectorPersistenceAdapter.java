package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.TupleVectorRepository;
import com.module06.backend.capture.domain.model.LayerName;
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

    /*
     * ⚠ 프로젝트 전체에 Clock 빈이 하나뿐이라(MeetingTimeConfiguration#meetingClock, KST)
     * 타입으로 주입된다. 캡처 전용 Clock 빈을 새로 만들면 안 된다.
     */
    private final Clock clock;

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

    @Override
    @Transactional(readOnly = true)
    public List<PendingVector> findPending(int maxAttempts, int limit) {
        return tupleVectorRepository
                .findByVectorSyncedFalseAndSyncAttemptsLessThanOrderByIdAsc(maxAttempts, Limit.of(limit))
                .stream()
                .map(entity -> new PendingVector(
                        entity.getId(),
                        entity.getCompanyId(),
                        LayerName.fromWireValue(entity.getLayer()),
                        entity.getInputText(),
                        entity.getPayload(),
                        entity.getDeptId(),
                        entity.getProvenance()))
                .toList();
    }

    /*
     * 반영 결과를 **행마다 자기 트랜잭션에서** 적는다(REQUIRES_NEW).
     *
     * 배치 하나를 한 트랜잭션으로 묶으면, 마지막 행에서 터졌을 때 앞의 성공까지 롤백된다 —
     * 그 예시들은 Qdrant 에 **이미 올라가 있는데** 원본은 안 올라간 것으로 남아, 다음 주기가
     * 같은 것을 또 올리고 임베딩 비용을 두 번 낸다. 인덱스 쪽 부수효과는 되돌릴 수 없으므로
     * 기록을 잘게 나누는 편이 맞다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSynced(long id, String pointId) {
        tupleVectorRepository.findById(id)
                .ifPresent(entity -> {
                    entity.markSynced(pointId, LocalDateTime.now(clock));
                    tupleVectorRepository.save(entity);
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSyncFailed(long id) {
        tupleVectorRepository.findById(id)
                .ifPresent(entity -> {
                    entity.markSyncFailed();
                    tupleVectorRepository.save(entity);
                });
    }
}
