package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.DeleteRecordingUseCase;

import java.time.LocalDateTime;

// 녹음 삭제 응답 JSON
public record DeleteRecordingResponse(
        LocalDateTime deletedAt,
        long freedBytes
) {

    // usecase 결과 → 응답 DTO
    public static DeleteRecordingResponse from(DeleteRecordingUseCase.Result result) {
        return new DeleteRecordingResponse(result.deletedAt(), result.freedBytes());
    }
}
