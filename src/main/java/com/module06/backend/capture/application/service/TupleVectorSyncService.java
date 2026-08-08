package com.module06.backend.capture.application.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.TupleVectorIndexPort;
import com.module06.backend.capture.application.port.out.TupleVectorIndexPort.IndexedVector;
import com.module06.backend.capture.application.port.out.TupleVectorIndexPort.VectorToIndex;
import com.module06.backend.capture.application.port.out.TupleVectorRepository;
import com.module06.backend.capture.application.port.out.TupleVectorRepository.PendingVector;

/*
 * few-shot 예시를 Qdrant 에 올리는 워커다(AI-08).
 *
 * <h2>왜 예약과 반영을 나눴나</h2>
 * RVW-02 는 사람이 검토를 확정하는 순간이고, 그 트랜잭션에는 action·review_log·
 * meeting_tuple_vector 세 쓰기가 이미 묶여 있다. 거기에 **외부 HTTP 호출을 끼우면** AI 서버가
 * 느릴 때 사람의 판정이 그만큼 늦어지고, 그 호출이 실패하면 판정 자체가 롤백된다 —
 * 라벨 한 건을 색인 못 한 대가로 사람이 내린 판단을 잃는 것이라 방향이 거꾸로다.
 *
 * 그래서 MySQL 에 먼저 커밋하고(원본), 인덱스는 이 워커가 뒤따라 채운다. 그 순서가 V5.10 이
 * vector_synced·sync_attempts 컬럼을 둔 이유이기도 하다.
 *
 * <h2>이 워커가 없으면 라벨이 쌓이기만 한다</h2>
 * 붙기 전까지 vector_synced 는 영원히 false 였고, 계층은 few-shot 없이 돌았다. 검토 화면에서
 * 사람이 고친 값이 다음 회의의 정확도로 돌아오는 경로가 **여기서 닫힌다.**
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TupleVectorSyncService {

    /*
     * 한 주기에 올리는 수. 크게 잡으면 임베딩 한 번에 오래 걸려 실패했을 때 통째로 다시
     * 올려야 하고, 작게 잡으면 밀린 라벨이 안 빠진다. AI-08 이 배치를 그대로 한 번에
     * 임베딩하므로 이 값이 곧 한 요청의 크기다.
     */
    private static final int BATCH_SIZE = 50;

    /*
     * 이 횟수만큼 실패한 행은 더 집지 않는다.
     *
     * **자르지 않으면 큐 하나가 아니라 큐 전체가 막힌다** — 계속 실패하는 행(깨진 payload,
     * 임베딩 한도 초과)을 매 주기 다시 집으면 오래된 순서 때문에 그 행이 항상 앞에 서고,
     * 뒤의 정상 행은 영원히 올라가지 않는다.
     *
     * 자른 행을 지우지는 않는다. 원본은 남아 있어야 원인을 고친 뒤 다시 올릴 수 있고,
     * 라벨 자체는 여전히 유효하다(few-shot 예시로 못 쓸 뿐이다).
     */
    private static final int MAX_ATTEMPTS = 5;

    private final TupleVectorRepository tupleVectorRepository;
    private final TupleVectorIndexPort tupleVectorIndexPort;

    /*
     * 밀린 예시를 한 배치 올린다.
     *
     * <h2>트랜잭션을 걸지 않는다</h2>
     * 이 메서드 안에서 외부 HTTP 를 부른다. 트랜잭션으로 감싸면 그 호출이 느린 동안 커넥션을
     * 쥐고 있게 되고, 배치 하나가 실패했을 때 앞서 성공한 행의 기록까지 함께 롤백된다 —
     * 그 예시들은 **Qdrant 에 이미 올라가 있는데** 원본은 안 올라간 것으로 남아, 다음 주기가
     * 같은 것을 또 올리고 임베딩 비용을 두 번 낸다. 인덱스 쪽 부수효과는 되돌릴 수 없으므로
     * 기록은 행마다 자기 트랜잭션에서 한다(어댑터의 REQUIRES_NEW).
     *
     * @return 이번 주기에 실제로 올린 수. 스케줄러가 로그로 쓰고, 테스트가 이 값으로 본다
     */
    public int syncOnce() {
        List<PendingVector> pending = tupleVectorRepository.findPending(MAX_ATTEMPTS, BATCH_SIZE);
        if (pending.isEmpty()) {
            return 0;
        }

        List<IndexedVector> indexed;
        try {
            indexed = tupleVectorIndexPort.upsert(pending.stream().map(this::toIndexRequest).toList());
        } catch (RuntimeException e) {
            /*
             * 배치 전체가 실패했다(AI 서버가 안 떴거나 네트워크). **예외를 밖으로 올리지 않는다** —
             * 스케줄러가 예외를 받으면 그 주기가 죽고, 실패 원인이 스케줄러 로그로만 남아
             * 어느 행이 밀렸는지가 안 보인다. 대신 전부 실패로 세어 시도 횟수를 올린다.
             */
            log.warn("벡터 색인 배치 실패 — 다음 주기에 다시 시도한다. 대상={}건", pending.size(), e);
            pending.forEach(vector -> tupleVectorRepository.markSyncFailed(vector.id()));
            return 0;
        }

        Map<Long, String> pointIdByVectorId = indexed.stream()
                .collect(Collectors.toMap(IndexedVector::vectorId, IndexedVector::pointId,
                        // 같은 행이 두 번 오면 나중 것을 쓴다. 어느 쪽이든 같은 포인트를 가리키므로
                        // 값은 같고, 여기서 터뜨리면 정상 배치가 통째로 밀린다.
                        (first, second) -> second));

        for (PendingVector vector : pending) {
            String pointId = pointIdByVectorId.get(vector.id());
            if (pointId == null) {
                /*
                 * 응답에 없는 행 = 안 올라간 행이다. **성공으로 세지 않는다** — 올라갔다고
                 * 표시하면 그 예시는 다시 올릴 기회를 영영 잃고, 검색에는 안 걸리는데 원본은
                 * "반영됨"으로 남아 아무도 빠진 것을 모른다.
                 */
                tupleVectorRepository.markSyncFailed(vector.id());
                continue;
            }
            tupleVectorRepository.markSynced(vector.id(), pointId);
        }

        int synced = pointIdByVectorId.size();
        if (synced != pending.size()) {
            log.warn("벡터 색인 일부 누락 — 대상={}건 반영={}건. 남은 것은 다음 주기가 다시 집는다",
                    pending.size(), synced);
        }
        return synced;
    }

    private VectorToIndex toIndexRequest(PendingVector vector) {
        return new VectorToIndex(
                vector.id(),
                vector.companyId(),
                vector.layer(),
                vector.inputText(),
                vector.payload(),
                vector.deptId(),
                vector.provenance());
    }
}
