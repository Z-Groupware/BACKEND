package com.module06.backend.capture.presentation.api.response;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.capture.application.result.DistributionConfirmed;

/*
 * RVW-05 응답이다.
 *
 * <h2>skipped 가 절반이다</h2>
 * 내보낸 수만 보여주면 사람은 **무엇이 안 나갔는지 모른 채** 화면을 닫는다. 검토가 끝났다고
 * 생각한 회의에서 액션 몇 건이 조용히 남는 것이 이 API 의 가장 나쁜 실패다.
 */
public record DistributionConfirmResponse(
        int dispatchedCount,
        LocalDateTime dispatchedAt,
        List<Skipped> skipped
) {

    public static DistributionConfirmResponse from(DistributionConfirmed confirmed) {
        return new DistributionConfirmResponse(
                confirmed.dispatchedCount(),
                confirmed.dispatchedAt(),
                confirmed.skipped().stream()
                        .map(skip -> new Skipped(skip.actionId(), skip.reason()))
                        .toList());
    }

    /* reason: STILL_PENDING(미검토) · REJECTED(반려) · NO_ASSIGNEE(담당자 미정) */
    public record Skipped(long actionId, String reason) {
    }
}
