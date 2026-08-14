package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.IssueManualRecordingUploadUrlUseCase;

// 수동 녹음 업로드 URL 발급 응답 JSON
public record ManualRecordingUploadUrlResponse(
        String s3Key,
        String uploadUrl,
        int expiresInSeconds
) {

    // usecase 결과 → 응답 DTO
    public static ManualRecordingUploadUrlResponse from(IssueManualRecordingUploadUrlUseCase.Result result) {
        return new ManualRecordingUploadUrlResponse(
                result.s3Key(),
                result.uploadUrl(),
                result.expiresInSeconds());
    }
}
