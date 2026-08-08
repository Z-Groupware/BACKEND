package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.VectorProvenance;

/*
 * AI-08(`POST /internal/vector/upsert`) 호출 포트다. few-shot 예시를 Qdrant 에 올린다.
 *
 * <h2>MySQL 이 원본이고 이쪽이 인덱스다</h2>
 * 라벨은 RVW-02 트랜잭션에서 이미 커밋됐다(meeting_tuple_vector). 여기서 실패해도 잃는 것은
 * **검색 품질뿐이고 라벨은 안전하다.** 순서를 뒤집어 여기부터 올리면 벡터는 검색에 걸리는데
 * 꺼낼 내용이 없는 상태가 생긴다.
 *
 * <h2>행 단위로 결과를 받는다</h2>
 * 배치 전체를 하나의 성공/실패로 받으면 **일부만 들어간 배치에서 어느 행을 다시 보낼지 알 수
 * 없어 전부 재시도하게 된다.** 그러면 이미 올라간 예시가 또 올라가고, 임베딩 비용이 그만큼
 * 두 번 나간다. 그래서 응답이 올라간 행만 지목한다.
 *
 * <h2>재시도를 여기서 돌리지 않는다</h2>
 * AiLayerPort 와 같은 판단이다 — Python 이 자기 안에서 백오프를 돌리고, 이쪽의 재시도 단위는
 * **다음 워커 주기**다. 여기서 또 돌리면 한 주기에 같은 배치를 여러 번 태운다.
 */
public interface TupleVectorIndexPort {

    /*
     * 예시들을 인덱스에 올린다.
     *
     * @return 실제로 올라간 것만. 요청보다 적을 수 있고, 그 차이가 곧 "다시 보내야 할 행"이다
     */
    List<IndexedVector> upsert(List<VectorToIndex> vectors);

    /*
     * 올릴 예시 하나.
     *
     * @param vectorId 원본 행 id(meeting_tuple_vector.id). **AI-08 이 이 값으로 포인트 id 를
     *                 결정적으로 만든다** — 그래서 같은 행을 다시 보내도 덮어써질 뿐 복제되지
     *                 않는다. 무작위 id 였다면 재시도마다 같은 예시가 쌓여 검색 상위를 채운다
     */
    record VectorToIndex(
            long vectorId,
            long companyId,
            LayerName layer,
            String inputText,
            String payload,
            Long deptId,
            VectorProvenance provenance
    ) {
    }

    /* 올라간 예시. pointId 는 원본에 되적어 두어야 나중에 다시 올리거나 지울 수 있다. */
    record IndexedVector(long vectorId, String pointId) {
    }
}
