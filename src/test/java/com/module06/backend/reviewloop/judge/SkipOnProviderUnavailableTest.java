package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 건너뜀/실패 분류 검증 — 키 없이 돌아간다(라이브 호출 없음).
 *
 * <p>이 분류가 틀리면 둘 중 하나가 된다. 너무 관대하면 진짜 판정 회귀가 건너뛰기로 숨고,
 * 너무 엄격하면 상시 빨간불로 돌아가 신호가 다시 죽는다.
 */
class SkipOnProviderUnavailableTest {

    @Test
    @DisplayName("키가 무효·만료면 건너뛴다 (401·403)")
    void 인증_실패는_건너뛴다() {
        assertThat(SkipOnProviderUnavailable.unavailableReason(apiError(401))).isNotNull();
        assertThat(SkipOnProviderUnavailable.unavailableReason(apiError(403))).isNotNull();
    }

    @Test
    @DisplayName("쿼터 초과면 건너뛴다 (429) — 시간이 지나면 풀리는 문제다")
    void 쿼터_초과는_건너뛴다() {
        assertThat(SkipOnProviderUnavailable.unavailableReason(apiError(429))).isNotNull();
    }

    @Test
    @DisplayName("제공자 장애면 건너뛴다 (5xx)")
    void 제공자_장애는_건너뛴다() {
        assertThat(SkipOnProviderUnavailable.unavailableReason(apiError(500))).isNotNull();
        assertThat(SkipOnProviderUnavailable.unavailableReason(apiError(503))).isNotNull();
    }

    @Test
    @DisplayName("연결 자체가 안 되면 건너뛴다 — 원인 사슬 안쪽의 IOException 까지 본다")
    void 네트워크_오류는_건너뛴다() {
        Throwable wrapped = new RuntimeException("Gemini 호출 실패", new ConnectException("timed out"));

        assertThat(SkipOnProviderUnavailable.unavailableReason(wrapped)).isNotNull();
        assertThat(SkipOnProviderUnavailable.unavailableReason(new IOException("boom"))).isNotNull();
    }

    @Test
    @DisplayName("잘못된 요청·없는 모델 id 는 실패로 남긴다 (400·404) — 우리 설정 버그라 고쳐져야 한다")
    void 우리_설정_문제는_실패로_남긴다() {
        assertThat(SkipOnProviderUnavailable.unavailableReason(apiError(400))).isNull();
        assertThat(SkipOnProviderUnavailable.unavailableReason(apiError(404))).isNull();
    }

    @Test
    @DisplayName("제공자가 정상 응답한 뒤의 단정 실패는 실패로 남긴다 — 이게 이 잡이 지켜보려던 회귀다")
    void 판정_불일치는_실패로_남긴다() {
        assertThat(SkipOnProviderUnavailable.unavailableReason(
                new AssertionError("N+1 씨앗 코드에서 최소 1건의 finding을 기대"))).isNull();
        assertThat(SkipOnProviderUnavailable.unavailableReason(
                new IllegalStateException("응답에 candidates 가 없다"))).isNull();
    }

    @Test
    @DisplayName("건너뜀 사유에 응답 본문을 담지 않는다 — 상태코드만으로 판별된다")
    void 사유에_응답_본문을_담지_않는다() {
        Throwable withBody = new IllegalStateException(
                "Gemini API 오류 403: {\"error\":{\"message\":\"API key not valid\",\"details\":\"SENSITIVE\"}}");

        String reason = SkipOnProviderUnavailable.unavailableReason(withBody);

        assertThat(reason).contains("403");
        assertThat(reason).doesNotContain("SENSITIVE").doesNotContain("API key not valid");
    }

    @Test
    @DisplayName("자기참조 cause 에도 멈춘다 — 무한 루프 방어")
    void 순환하는_원인_사슬에도_끝난다() {
        RuntimeException loop = new SelfCausingException();

        assertThat(SkipOnProviderUnavailable.unavailableReason(loop)).isNull();
    }

    @Test
    @DisplayName("메시지가 형식과 다르면 상태코드를 -1 로 본다")
    void 형식이_아니면_상태코드가_없다() {
        assertThat(SkipOnProviderUnavailable.statusOf(null)).isEqualTo(-1);
        assertThat(SkipOnProviderUnavailable.statusOf("그냥 실패")).isEqualTo(-1);
        assertThat(SkipOnProviderUnavailable.statusOf("Gemini API 오류 429: rate limited")).isEqualTo(429);
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
