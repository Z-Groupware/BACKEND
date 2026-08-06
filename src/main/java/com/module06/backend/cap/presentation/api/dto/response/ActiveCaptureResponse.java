package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.GetActiveCaptureUseCase;

// 진행 중 캡처 조회 응답 JSON. 진행 중인 캡처가 없으면 컨트롤러가 이 객체 대신 data:null을 내려준다.
public record ActiveCaptureResponse(
        Long meetingId,
        Long captureSessionId,
        int segmentSeq,
        int lastSeq,
        Long recorderPersonId,
        boolean canTakeover,
        long elapsedMs
) {

    // usecase 결과 → 응답 DTO
    public static ActiveCaptureResponse from(GetActiveCaptureUseCase.Result result) {
        return new ActiveCaptureResponse(
                result.meetingId(),
                result.captureSessionId(),
                result.segmentSeq(),
                result.lastSeq(),
                result.recorderPersonId(),
                result.canTakeover(),
                result.elapsedMs());
    }
}
