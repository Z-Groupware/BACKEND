package com.module06.backend.capture.application.port.out;

import com.module06.backend.capture.domain.model.LayerName;

/*
 * few-shot 예시 원본(meeting_tuple_vector · V5.10)을 쌓는다. AI-08 이 Qdrant 에 올릴 대상이다.
 *
 * <h2>MySQL 이 원본이고 Qdrant 는 인덱스다</h2>
 * 여기에 먼저 커밋하고 Qdrant 는 나중에 넣는다. 실패하면 vector_synced=false 로 남고 재시도
 * 워커가 처리한다 — **라벨은 이미 안전하다.** 순서를 뒤집으면 벡터는 있는데 원본이 없는
 * 상태가 생기고, 그 벡터는 검색에 걸리는데 내용을 꺼낼 수 없다.
 *
 * 그래서 이 포트는 Qdrant 를 모른다. "예약했다"까지가 이쪽 책임이고(RVW-02 응답의
 * vectorQueued), 실제 임베딩은 AI-08 이 한다.
 */
public interface TupleVectorRepository {

    /*
     * 예시 하나를 예약한다.
     *
     * @param inputText 임베딩 대상 = **근거 발화 원문**이다. 확정 tuple 이 아니다 —
     *                  검색 시점에 손에 있는 것은 tuple 이 아니라 새 발화이므로, tuple 을
     *                  임베딩하면 쿼리와 키가 다른 공간에 놓여 유사도가 망가진다(V5.10 주석)
     * @param payload   확정 tuple. 검색 결과로 그대로 실려 나간다
     */
    void enqueue(VectorEntry entry);

    record VectorEntry(
            long companyId,
            long meetingId,
            LayerName layer,
            String inputText,
            String payload,
            long reviewLogId
    ) {
    }
}
