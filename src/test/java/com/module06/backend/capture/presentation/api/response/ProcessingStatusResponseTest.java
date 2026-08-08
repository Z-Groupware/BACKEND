package com.module06.backend.capture.presentation.api.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.module06.backend.capture.application.result.ProcessingStatus;
import com.module06.backend.capture.application.result.ProcessingStatus.LayerProgress;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAP-06 응답 계약.
 *
 * <p>{@code gaps} 는 한 번 방향이 뒤집힌 필드다. 처음에는 "확인하지 않은 것을 확인된 것처럼
 * 보여주지 않으려고" 빼뒀는데, 계약상 필수 배열이라 프론트가 그 자리에서 터진다(PR #84 지적).
 * 그래서 배열은 계약대로 주고 {@code gapsChecked} 로 "그 빈 배열이 확인 결과인가"를 말한다.
 * 이 테스트가 두 조건을 같이 고정한다 — 한쪽만 남으면 다시 뒤집힌다.
 */
class ProcessingStatusResponseTest {

    @Test
    @DisplayName("gaps 는 항상 존재한다 — 프론트가 undefined 를 읽지 않게")
    void gaps는_항상_존재한다() {
        ProcessingStatusResponse response = ProcessingStatusResponse.from(
                ProcessingStatus.of(List.of()));

        assertThat(response.gaps()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("gapsChecked 는 false — 빈 배열이 '구멍 없음'을 뜻하지 않는다")
    void 확인하지_않았음을_말한다() {
        ProcessingStatusResponse response = ProcessingStatusResponse.from(
                ProcessingStatus.of(List.of()));

        // stt_gap 을 채우는 경로(조립·Transcribe)가 붙기 전까지는 false 여야 한다.
        // true 로 바뀌면 화면이 "확인했고 구멍이 없다"로 읽고 배너를 띄우지 않는다.
        assertThat(response.gapsChecked()).isFalse();
    }

    @Test
    @DisplayName("계층은 전송 값(L1.5)으로 내려간다 — enum 이름(L1_5)이 아니다")
    void 계층은_전송값으로_내려간다() {
        ProcessingStatusResponse response = ProcessingStatusResponse.from(
                ProcessingStatus.of(List.of(
                        new LayerProgress(LayerName.L1_5, LayerStatus.DONE, 100, 20, false))));

        assertThat(response.layers()).hasSize(1);
        assertThat(response.layers().get(0).layer()).isEqualTo("L1.5");
    }

    @Test
    @DisplayName("멈춘 RUNNING 은 전체 FAILED 로 내려간다 — 「AI 처리 중」이 끝나지 않으면 안 된다")
    void 멈춘_계층은_실패로_접힌다() {
        ProcessingStatusResponse response = ProcessingStatusResponse.from(
                ProcessingStatus.of(List.of(
                        new LayerProgress(LayerName.L1_5, LayerStatus.DONE, 100, 20, false),
                        new LayerProgress(LayerName.L2, LayerStatus.RUNNING, 0, 0, true))));

        assertThat(response.status()).isEqualTo("FAILED");
        // 계층 자체는 저장된 값(RUNNING)을 그대로 말하고, 멈췄다는 사실은 따로 준다 —
        // 화면이 "중단됨 · 다시 분석"으로 안내할 근거다.
        assertThat(response.layers().get(1).status()).isEqualTo("RUNNING");
        assertThat(response.layers().get(1).stalled()).isTrue();
    }

    @Test
    @DisplayName("살아 있는 RUNNING 은 그대로 RUNNING — 도는 분석을 멈춘 것으로 보이면 안 된다")
    void 살아있는_계층은_처리중으로_남는다() {
        ProcessingStatusResponse response = ProcessingStatusResponse.from(
                ProcessingStatus.of(List.of(
                        new LayerProgress(LayerName.L2, LayerStatus.RUNNING, 0, 0, false))));

        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(response.layers().get(0).stalled()).isFalse();
    }
}
