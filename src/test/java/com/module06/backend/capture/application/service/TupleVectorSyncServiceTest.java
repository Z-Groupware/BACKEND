package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.out.TupleVectorIndexPort;
import com.module06.backend.capture.application.port.out.TupleVectorRepository;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.VectorProvenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * AI-08 · few-shot 예시를 Qdrant 에 올리는 워커.
 *
 * <p>검증의 축은 "올라가는가"가 아니라 <b>안 올라간 것이 올라간 것으로 기록되지 않는가</b>다.
 * 잘못 표시하면 그 예시는 다시 올릴 기회를 영영 잃고, 검색에는 안 걸리는데 원본은 "반영됨"으로
 * 남아 <b>아무도 빠진 것을 모른다.</b> 라벨은 지나간 회의라 다시 만들 수 없다.
 */
class TupleVectorSyncServiceTest {

    @Test
    @DisplayName("올라간 예시는 포인트 id 와 함께 반영으로 표시한다")
    void 올라간_예시를_반영으로_표시한다() {
        RecordingVectorRepository vectors = new RecordingVectorRepository(pending(1L), pending(2L));
        FakeIndexPort index = FakeIndexPort.succeedingAll();

        int synced = new TupleVectorSyncService(vectors, index).syncOnce();

        assertThat(synced).isEqualTo(2);
        assertThat(vectors.synced).containsEntry(1L, "point-1").containsEntry(2L, "point-2");
        assertThat(vectors.failed).isEmpty();
    }

    @Test
    @DisplayName("응답에 없는 행은 실패로 센다 — 성공으로 표시하면 그 예시는 영영 안 올라간다")
    void 응답에_없는_행은_실패로_센다() {
        RecordingVectorRepository vectors = new RecordingVectorRepository(pending(1L), pending(2L));
        // AI-08 이 행 단위로 답한다. 2번은 못 올라갔다.
        FakeIndexPort index = FakeIndexPort.succeedingOnly(1L);

        int synced = new TupleVectorSyncService(vectors, index).syncOnce();

        assertThat(synced).isEqualTo(1);
        assertThat(vectors.synced).containsOnlyKeys(1L);
        // 다음 주기가 다시 집을 수 있어야 한다.
        assertThat(vectors.failed).containsExactly(2L);
    }

    @Test
    @DisplayName("배치가 통째로 실패해도 예외를 올리지 않는다 — 스케줄러가 죽으면 색인이 영구히 멈춘다")
    void 배치_실패는_예외로_올리지_않는다() {
        RecordingVectorRepository vectors = new RecordingVectorRepository(pending(1L), pending(2L));
        FakeIndexPort index = FakeIndexPort.throwing();

        TupleVectorSyncService service = new TupleVectorSyncService(vectors, index);

        assertThatCode(service::syncOnce).doesNotThrowAnyException();
        // 전부 시도 횟수만 오른다 — vector_synced 는 false 그대로라 다음 주기에 다시 잡힌다.
        assertThat(vectors.synced).isEmpty();
        assertThat(vectors.failed).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("올릴 것이 없으면 AI 서버를 부르지 않는다")
    void 대상이_없으면_호출하지_않는다() {
        RecordingVectorRepository vectors = new RecordingVectorRepository();
        FakeIndexPort index = FakeIndexPort.succeedingAll();

        int synced = new TupleVectorSyncService(vectors, index).syncOnce();

        assertThat(synced).isZero();
        assertThat(index.called).isFalse();
    }

    @Test
    @DisplayName("계속 실패한 행은 조회에서 빠진다 — 큐 하나가 아니라 큐 전체가 막힌다")
    void 시도_횟수_상한을_저장소에_넘긴다() {
        RecordingVectorRepository vectors = new RecordingVectorRepository(pending(1L));

        new TupleVectorSyncService(vectors, FakeIndexPort.succeedingAll()).syncOnce();

        // 상한을 안 넘기면 깨진 행 하나가 매 주기 앞에 서서 뒤의 정상 행을 영원히 막는다.
        assertThat(vectors.requestedMaxAttempts).isPositive();
        assertThat(vectors.requestedLimit).isPositive();
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private static TupleVectorRepository.PendingVector pending(long id) {
        return new TupleVectorRepository.PendingVector(
                id, 7L, LayerName.L4, "서준님이 정리해주세요.",
                "{\"title\":\"정리\"}", null, VectorProvenance.HUMAN_VERIFIED);
    }

    private static final class RecordingVectorRepository implements TupleVectorRepository {

        private final List<PendingVector> pending;
        private final java.util.Map<Long, String> synced = new java.util.LinkedHashMap<>();
        private final List<Long> failed = new ArrayList<>();
        private int requestedMaxAttempts;
        private int requestedLimit;

        private RecordingVectorRepository(PendingVector... pending) {
            this.pending = List.of(pending);
        }

        @Override
        public List<PendingVector> findPending(int maxAttempts, int limit) {
            this.requestedMaxAttempts = maxAttempts;
            this.requestedLimit = limit;
            return pending;
        }

        @Override
        public void markSynced(long id, String pointId) {
            synced.put(id, pointId);
        }

        @Override
        public void markSyncFailed(long id) {
            failed.add(id);
        }

        /* 예약은 RVW-02 의 몫이다 — 이 워커는 이미 예약된 것만 본다. */
        @Override
        public void enqueue(VectorEntry entry) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeIndexPort implements TupleVectorIndexPort {

        private final List<Long> succeeding;
        private final boolean throwing;
        private boolean called;

        private FakeIndexPort(List<Long> succeeding, boolean throwing) {
            this.succeeding = succeeding;
            this.throwing = throwing;
        }

        private static FakeIndexPort succeedingAll() {
            return new FakeIndexPort(null, false);
        }

        private static FakeIndexPort succeedingOnly(Long... ids) {
            return new FakeIndexPort(List.of(ids), false);
        }

        private static FakeIndexPort throwing() {
            return new FakeIndexPort(null, true);
        }

        @Override
        public List<IndexedVector> upsert(List<VectorToIndex> vectors) {
            called = true;
            if (throwing) {
                // 실물에서는 연결 실패·타임아웃이 이 모양으로 온다.
                throw new IllegalStateException("AI 서버에 닿지 않는다");
            }
            return vectors.stream()
                    .filter(vector -> succeeding == null || succeeding.contains(vector.vectorId()))
                    .map(vector -> new IndexedVector(vector.vectorId(), "point-" + vector.vectorId()))
                    .toList();
        }
    }
}
