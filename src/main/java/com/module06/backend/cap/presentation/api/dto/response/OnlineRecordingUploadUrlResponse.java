package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.IssueOnlineRecordingUploadUrlUseCase;

/* 프론트가 S3 PUT과 최종 MEET-18 요청에 사용할 업로드 정보를 반환한다. */
public record OnlineRecordingUploadUrlResponse(
        String s3Key,
        String presignedUrl,
        int expiresInSeconds
) {

    public static OnlineRecordingUploadUrlResponse from(IssueOnlineRecordingUploadUrlUseCase.Result result) {
        return new OnlineRecordingUploadUrlResponse(
                result.s3Key(), result.presignedUrl(), result.expiresInSeconds());
    }
}
