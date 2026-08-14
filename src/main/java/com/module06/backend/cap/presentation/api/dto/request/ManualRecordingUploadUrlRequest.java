package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.IssueManualRecordingUploadUrlCommand;
import jakarta.validation.constraints.NotBlank;

// 수동 녹음 업로드 URL 발급 요청 body. 컨트롤러의 path/토큰과 합쳐 Command로 변환한다.
public record ManualRecordingUploadUrlRequest(
        @NotBlank String fileName,
        @NotBlank String contentType
) {

    public IssueManualRecordingUploadUrlCommand toCommand(Long meetingId, Long callerId) {
        return new IssueManualRecordingUploadUrlCommand(meetingId, callerId, fileName, contentType);
    }
}
