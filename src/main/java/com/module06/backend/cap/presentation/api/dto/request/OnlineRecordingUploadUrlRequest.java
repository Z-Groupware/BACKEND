package com.module06.backend.cap.presentation.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.module06.backend.cap.application.command.IssueOnlineRecordingUploadUrlCommand;

/* 비대면 회의 녹음의 S3 직접 업로드 URL 발급 요청이다. */
public record OnlineRecordingUploadUrlRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Positive Long sizeBytes
) {

    public IssueOnlineRecordingUploadUrlCommand toCommand(Long companyId, Long memberId) {
        return new IssueOnlineRecordingUploadUrlCommand(companyId, memberId, fileName, contentType, sizeBytes);
    }
}
