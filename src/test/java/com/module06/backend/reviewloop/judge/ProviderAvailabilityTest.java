package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "제공자 사용 불가" 분류 검증 — 키 없이 돌아간다(라이브 호출 없음).
 *
 * <p>이 분류가 틀리면 둘 중 하나가 된다. 너무 관대하면 진짜 판정 회귀가 건너뛰기로 숨고,
 * 너무 엄격하면 상시 빨간불·push 차단으로 돌아가 게이트가 죽는다.
 */
class ProviderAvailabilityTest {

    @Test
    @DisplayName("키가 무효·만료면 사용 불가 (401·403)")
    void 인증_실패는_사용_불가다() {
        assertThat(ProviderAvailability.unavailableReason(apiError(401))).isNotNull();
        assertThat(ProviderAvailability.unavailableReason(apiError(403))).isNotNull();
    }

    @Test
    @DisplayName("쿼터·크레딧 소진이면 사용 불가 (429) — 2026-08-05 실제로 겪은 경로다")
    void 쿼터_소진은_사용_불가다() {
        Throwable depleted = new IllegalStateException(
                "Gemini API 오류 429: {\"error\":{\"code\":429,"
                        + "\"message\":\"Your prepayment credits are depleted.\","
                        + "\"status\":\"RESOURCE_EXHAUSTED\"}}");

        assertThat(ProviderAvailability.unavailableReason(depleted)).isNotNull();
    }

    @Test
    @DisplayName("제공자 장애면 사용 불가 (5xx)")
    void 제공자_장애는_사용_불가다() {
        assertThat(ProviderAvailability.unavailableReason(apiError(500))).isNotNull();
        assertThat(ProviderAvailability.unavailableReason(apiError(503))).isNotNull();
    }

    @Test
    @DisplayName("연결 자체가 안 되면 사용 불가 — 원인 사슬 안쪽의 IOException 까지 본다")
    void 네트워크_오류는_사용_불가다() {
        Throwable wrapped = new RuntimeException("Gemini 호출 실패", new ConnectException("timed out"));

        assertThat(ProviderAvailability.unavailableReason(wrapped)).isNotNull();
        assertThat(ProviderAvailability.unavailableReason(new IOException("boom"))).isNotNull();
    }

    @Test
    @DisplayName("잘못된 요청·없는 모델 id 는 우리 문제다 (400·404) — 빨간불로 남아야 고쳐진다")
    void 우리_설정_문제는_사용_불가가_아니다() {
        assertThat(ProviderAvailability.unavailableReason(apiError(400))).isNull();
        assertThat(ProviderAvailability.unavailableReason(apiError(404))).isNull();
    }

    @Test
    @DisplayName("제공자가 정상 응답한 뒤의 실패는 코드 판정이다 — 게이트가 지켜보려던 것")
    void 판정_불일치는_사용_불가가_아니다() {
        assertThat(ProviderAvailability.unavailableReason(
                new AssertionError("N+1 씨앗 코드에서 최소 1건의 finding을 기대"))).isNull();
        assertThat(ProviderAvailability.unavailableReason(
                new IllegalStateException("응답에 candidates 가 없다"))).isNull();
    }

    @Test
    @DisplayName("사유에 응답 본문을 담지 않는다 — 상태코드만으로 판별된다")
    void 사유에_응답_본문을_담지_않는다() {
        Throwable withBody = new IllegalStateException(
                "Gemini API 오류 403: {\"error\":{\"message\":\"API key not valid\",\"details\":\"SENSITIVE\"}}");

        String reason = ProviderAvailability.unavailableReason(withBody);

        assertThat(reason).contains("403");
        assertThat(reason).doesNotContain("SENSITIVE").doesNotContain("API key not valid");
    }

    @Test
    @DisplayName("자기참조 cause 에도 멈춘다 — 무한 루프 방어")
    void 순환하는_원인_사슬에도_끝난다() {
        assertThat(ProviderAvailability.unavailableReason(new SelfCausingException())).isNull();
    }

    @Test
    @DisplayName("메시지가 형식과 다르면 상태코드가 없다")
    void 형식이_아니면_상태코드가_없다() {
        assertThat(ProviderAvailability.statusOf(null)).isEqualTo(-1);
        assertThat(ProviderAvailability.statusOf("그냥 실패")).isEqualTo(-1);
        assertThat(ProviderAvailability.statusOf("Gemini API 오류 429: rate limited")).isEqualTo(429);
    }

    private static Throwable apiError(int status) {
        return new IllegalStateException("Gemini API 오류 " + status + ": {\"error\":\"...\"}");
    }

    /** getCause 가 자기 자신을 돌려주는 병리적 예외. */
    private static final class SelfCausingException extends RuntimeException {
        private SelfCausingException() {
            super("순환");
        }

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }
}
