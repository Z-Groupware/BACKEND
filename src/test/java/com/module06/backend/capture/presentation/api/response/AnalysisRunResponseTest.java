package com.module06.backend.capture.presentation.api.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.domain.model.LayerName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ANLZ-01 응답 계약.
 *
 * <p>명세는 202 · {@code {"status": "QUEUED"}} 를 전제하지만 큐가 없어 실제 결과를 담는다.
 * 그래서 <b>상태값이 곧 계약</b>이고, 화면 분기가 여기에 붙는다.
 *
 * <p>특히 {@code SUPERSEDED} 는 {@code ALREADY_RUNNING} 과 뜻이 반대다 — 그쪽은 "기다리면
 * 결과가 나온다"이고 이쪽은 <b>"이 실행이 이미 낡았다"</b>이다. 화면이 둘을 같이 다루면
 * 재시도할 수 없는 요청에 재시도 버튼이 뜬다. 그 구분을 이 테스트가 고정한다(#134).
 */
class AnalysisRunResponseTest {

    @Test
    @DisplayName("SUPERSEDED 는 retryable=false 다 — 다시 눌러도 더 새 실행과 순서를 다투기만 한다")
    void 밀린_실행은_재시도_대상이_아니다() {
        AnalysisRunResponse response = AnalysisRunResponse.from(
                AnalysisOutcome.superseded(LayerName.L2));

        assertThat(response.status()).isEqualTo("SUPERSEDED");
        assertThat(response.retryable()).isFalse();
        // 물러난 계층을 알려준다 — 어디까지 갔다가 밀렸는지가 CAP-06 과 맞춰 읽힌다.
        assertThat(response.failedLayer()).isEqualTo("L2");
        assertThat(response.errorCode()).isNull();
        assertThat(response.message()).isNotBlank();
    }

    @Test
    @DisplayName("계층에 닿기 전에 밀리면 failedLayer 가 없다 — 없는 계층 이름을 지어내지 않는다")
    void 번호_발급에서_밀리면_계층이_없다() {
        AnalysisRunResponse response = AnalysisRunResponse.from(AnalysisOutcome.superseded(null));

        assertThat(response.status()).isEqualTo("SUPERSEDED");
        assertThat(response.failedLayer()).isNull();
    }

    @Test
    @DisplayName("ALREADY_RUNNING 과 섞이지 않는다 — 하나는 기다리는 것이고 하나는 낡은 것이다")
    void 진행_중과_밀림은_다른_상태다() {
        AnalysisRunResponse running = AnalysisRunResponse.from(
                AnalysisOutcome.alreadyRunning(LayerName.L2));
        AnalysisRunResponse superseded = AnalysisRunResponse.from(
                AnalysisOutcome.superseded(LayerName.L2));

        assertThat(running.status()).isEqualTo("ALREADY_RUNNING");
        assertThat(superseded.status()).isEqualTo("SUPERSEDED");
        assertThat(running.status()).isNotEqualTo(superseded.status());
    }

    @Test
    @DisplayName("실패는 errorCode 와 retryable 을 그대로 싣는다 — ANLZ-02 가 그 값으로 재개한다")
    void 실패는_재개_정보를_싣는다() {
        AnalysisRunResponse response = AnalysisRunResponse.from(
                AnalysisOutcome.failed(LayerName.L3, "PROVIDER_TIMEOUT", "응답이 없습니다.", true));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failedLayer()).isEqualTo("L3");
        assertThat(response.errorCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(response.retryable()).isTrue();
    }
}
