package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.RegisterManualRecordingUseCase;

// 수동 녹음 업로드 응답 JSON
public record ManualRecordingResponse(
        Long meetingId,
        long durationMs,
        long sizeBytes,
        String status
) {

    // usecase 결과 → 응답 DTO
    public static ManualRecordingResponse from(RegisterManualRecordingUseCase.Result result) {
        return new ManualRecordingResponse(
                result.meetingId(),
                result.durationMs(),
                result.sizeBytes(),
                result.status());
    }
}
