package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.GetPlaybackUrlUseCase;

// 재생용 presigned URL 응답 JSON
public record PlaybackUrlResponse(
        String url,
        int expiresIn,
        long durationMs
) {

    // usecase 결과 → 응답 DTO
    public static PlaybackUrlResponse from(GetPlaybackUrlUseCase.Result result) {
        return new PlaybackUrlResponse(result.url(), result.expiresIn(), result.durationMs());
    }
}
