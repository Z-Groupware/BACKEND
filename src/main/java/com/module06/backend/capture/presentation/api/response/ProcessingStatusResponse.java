package com.module06.backend.capture.presentation.api.response;

import java.util.List;

import com.module06.backend.capture.application.result.ProcessingStatus;

/*
 * CAP-06 응답이다.
 *
 * 명세의 blocks · gaps · estimatedRemainingSec 는 **아직 넣지 않는다.** STT 블록과 stt_gap 을
 * 채우는 쪽(조립·Transcribe)이 붙지 않았고, 빈 값으로 내려주면 화면이 "구멍 없음"으로 읽어
 * 배너를 띄우지 않는다. 확인되지 않은 것을 확인된 것처럼 보여주는 쪽이 훨씬 비싸다.
 */
public record ProcessingStatusResponse(
        String status,
        List<LayerResponse> layers
) {

    public static ProcessingStatusResponse from(ProcessingStatus status) {
        return new ProcessingStatusResponse(
                status.status().name(),
                status.layers().stream()
                        .map(layer -> new LayerResponse(
                                layer.layer().wireValue(),
                                layer.status().name(),
                                layer.tokensIn(),
                                layer.tokensOut()))
                        .toList());
    }

    /* layer 는 "L1.5"·"L3.5" 같은 전송 값이다. enum 이름(L1_5)이 아니다. */
    public record LayerResponse(String layer, String status, int tokensIn, int tokensOut) {
    }
}
