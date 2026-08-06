package com.module06.backend.capture.application.result;

import java.time.LocalDateTime;
import java.util.List;

/*
 * RVW-05 결과다.
 *
 * <h2>skipped 가 응답의 절반이다</h2>
 * 내보낸 수만 돌려주면 사람은 **무엇이 안 나갔는지 모른 채** 화면을 닫는다. 검토가 끝났다고
 * 생각한 회의에서 액션 몇 건이 조용히 남는 것이 이 API 의 가장 나쁜 실패다 — 그 할 일은
 * 아무의 보드에도 없고, 아무도 그 사실을 모른다.
 */
public record DistributionConfirmed(
        int dispatchedCount,
        LocalDateTime dispatchedAt,
        List<SkippedAction> skipped
) {

    /*
     * 내보내지 않은 액션과 그 이유.
     *
     *   STILL_PENDING  아직 사람이 검토하지 않았다(?confirm=true 로 강행했을 때만 나온다)
     *   REJECTED       사람이 반려했다. 보드로 가면 안 되는 것이 반려의 뜻이다
     *   NO_ASSIGNEE    담당자가 정해지지 않았다. 나가면 아무도 자기 일로 보지 않는다
     *                  (C 도메인과 2026-08-07 합의 — 생성은 열고 확정에서 막는다)
     */
    public record SkippedAction(long actionId, String reason) {
    }
}
