package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

/**
 * 라이브 테스트에서 <b>제공자를 쓸 수 없는 상태</b>를 실패가 아니라 건너뜀으로 집계한다.
 *
 * <p>판정 기준은 {@link ProviderAvailability} 하나만 쓴다 — 훅(pre-push)이 돌리는
 * {@link ReviewLoopRunner}와 CI 라이브 테스트가 같은 기준으로 갈라야, 한쪽만 고쳐져
 * "훅은 통과했는데 CI만 빨간불" 같은 상태가 생기지 않는다.
 *
 * <p>키가 <b>없으면</b> {@code @EnabledIfEnvironmentVariable}가 애초에 테스트를 끈다.
 * 이 확장이 다루는 것은 <b>키가 있는데도 못 쓰는</b> 경우다(쿼터·크레딧 소진, 만료된 키,
 * 제공자 장애, 네트워크). 2026-08-05에 실제로 크레딧 소진으로 5건이 실패했다.
 *
 * <p>원본 예외는 삼키지 않는다 — 건너뛴 사유에는 상태코드만 담기므로, 응답 본문이 필요한
 * 진단은 실패 시 업로드되는 테스트 리포트 아티팩트를 본다({@code gate2-judge.yml}).
 */
final class SkipOnProviderUnavailable implements TestExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        String reason = ProviderAvailability.unavailableReason(throwable);
        if (reason != null) {
            // 건너뜀은 Gradle 로그 안쪽에만 남는다. 잡이 초록불이면 아무도 그 로그를 펴지 않으므로
            // CI 요약에 배너를 하나 올린다 — "연동이 살아있는지 확인하지 못했다"가 보여야 한다.
            // 감사 로그에는 남기지 않는다: 이 잡은 PR 코드를 리뷰하지 않으므로(스모크 테스트)
            // 거기 섞으면 '리뷰 없이 나간 건수'가 부풀려진다.
            GateSkipRecorder.warnSmokeSkipped(reason);
            // TestAbortedException → 실패가 아니라 '건너뜀'으로 집계된다.
            Assumptions.abort("라이브 판정을 건너뛴다 — " + reason);
        }
        throw throwable;
    }
}
