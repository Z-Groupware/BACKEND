package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.VectorProvenance;

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

    /*
     * 아직 Qdrant 에 못 올라간 예시를 가져온다(vector_synced=false).
     *
     * <h2>왜 시도 횟수로 자르나</h2>
     * 어떤 행은 몇 번을 보내도 실패한다 — payload 가 깨졌거나 텍스트가 임베딩 한도를 넘는
     * 경우다. 그걸 계속 다시 집으면 **워커가 그 행에 붙들려 뒤의 정상 행이 영원히 안 올라간다.**
     * 큐 하나가 막히는 것이 아니라 큐 전체가 막히는 종류의 실패다.
     *
     * 자른 행은 지우지 않는다. 원본은 남아 있어야 나중에 원인을 고친 뒤 다시 올릴 수 있고,
     * 라벨 자체는 이미 유효하다(few-shot 예시로 못 쓸 뿐이다).
     *
     * @param maxAttempts 이 횟수 **이상** 실패한 행은 대상에서 뺀다
     * @param limit       한 번에 가져올 수
     */
    List<PendingVector> findPending(int maxAttempts, int limit);

    /*
     * 올라간 예시 하나. 인덱스 상태를 원본에 되적는다.
     *
     * @param pointId AI-08 이 돌려준 Qdrant 포인트 id. **저장해야 나중에 다시 올리거나 지울 수
     *                있다** — 없으면 그 포인트를 우리가 만든 것인지 확인할 방법이 없다
     */
    void markSynced(long id, String pointId);

    /*
     * 못 올라간 예시. 시도 횟수만 올린다.
     *
     * 실패 사유를 저장하지 않는다 — 컬럼이 없고, 여기서 만드는 실패는 대부분 "AI 서버가 잠깐
     * 안 떴다"라서 행마다 남길 값이 아니다. 사유는 로그가 갖고, 이 컬럼은 **몇 번 시도했는지**만
     * 답한다(그게 자르는 기준이다).
     */
    void markSyncFailed(long id);

    /*
     * 올려야 할 예시 하나.
     *
     * @param inputText 임베딩 대상 = 근거 발화 원문
     * @param payload   확정 tuple(JSON 문자열). AI-08 으로 그대로 넘어간다
     * @param deptId    팀 스코프. 지금은 항상 null 이다 — 예약하는 쪽(RVW-02)이 채우지 않는다
     */
    record PendingVector(
            long id,
            long companyId,
            LayerName layer,
            String inputText,
            String payload,
            Long deptId,
            VectorProvenance provenance
    ) {
    }
}
