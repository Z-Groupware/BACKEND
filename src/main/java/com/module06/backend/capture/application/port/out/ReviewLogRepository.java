package com.module06.backend.capture.application.port.out;

import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.RejectReason;
import com.module06.backend.capture.domain.model.ReviewDecision;
import com.module06.backend.capture.domain.model.ReviewTargetType;

/*
 * 사람 라벨 로그(review_log · V5.9)를 남긴다. **특화 모델의 유일한 연료다.**
 *
 * 지금 넣지 않으면 나중에 붙여도 그때까지의 데이터가 없다 — 지나간 회의는 다시 만들 수 없다.
 *
 * <h2>append-only 다</h2>
 * 고치거나 지우는 메서드를 두지 않는다. 같은 액션을 두 번 판정하면 행이 둘 남는 것이 맞다 —
 * 사람이 마음을 바꾼 것도 라벨이고, 덮어쓰면 "처음에 무엇이 틀렸다고 봤는지"가 사라진다.
 * 그래서 이 테이블에는 updated_at 도 없다.
 */
public interface ReviewLogRepository {

    /*
     * 라벨 하나를 남긴다.
     *
     * @return 남긴 행의 id. 벡터(meeting_tuple_vector.review_log_id)가 이 값으로 자기 출처를
     *         가리키므로 필요하다 — 나중에 "이 few-shot 예시가 어느 판정에서 나왔나"를 되짚는다
     */
    long append(ReviewLogEntry entry);

    /*
     * 남길 라벨 하나.
     *
     * @param layer        이 라벨이 교정하는 계층. 사유가 있으면 사유가 정하고(RejectReason),
     *                     CONFIRM 이면 액션을 만든 계층(L4)이다
     * @param inputContext 재현 불가한 값이라 **넉넉히 담는다** — 근거 발화 · 참석자 명단 ·
     *                     주제. 지나간 회의는 다시 만들 수 없다(V5.9 주석)
     * @param llmOutput    AI 가 낸 원본 값. 수동 추가 액션이면 그런 값이 없다
     * @param humanValue   사람이 고친 값. CONFIRM 이면 null 이다 — llmOutput 과 같다는 뜻이고,
     *                     같은 값을 두 번 적으면 "고쳤는데 우연히 같았다"와 구분되지 않는다
     */
    record ReviewLogEntry(
            long companyId,
            long meetingId,
            /*
             * 무엇에 대한 라벨인가. ACTION=action.id / SUMMARY_ITEM=meeting_decision.id (V5.9).
             *
             * 예전에는 필드가 {@code actionId} 하나뿐이어서 액션 라벨만 남길 수 있었다.
             * ANLZ-04(요약 수정)가 붙으면서 넓혔다 — **요약도 라벨이다**(명세 처리 정책).
             * 둘을 한 표에 담는 이유는 채점·few-shot 조회가 같은 질의를 쓰기 때문이고,
             * 섞이지 않는 것은 조회 쪽이 target_type 으로 가른다(QualityMetricsJdbcAdapter).
             */
            ReviewTargetType targetType,
            long targetId,
            LayerName layer,
            ReviewDecision decision,
            RejectReason rejectReason,
            String inputContext,
            String llmOutput,
            String humanValue,
            long confirmedBy,
            String modelName,
            String promptVersion,
            boolean manual
    ) {
    }
}
