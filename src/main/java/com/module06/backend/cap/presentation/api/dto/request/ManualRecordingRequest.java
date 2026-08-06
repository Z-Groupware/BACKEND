package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 수동 녹음 업로드 요청 body. 컨트롤러의 path/토큰과 합쳐 Command로 변환한다.
public record ManualRecordingRequest(
        @NotBlank String s3Key,
        @NotNull @Positive Long sizeBytes
) {

    public RegisterManualRecordingCommand toCommand(Long meetingId, Long callerId) {
        return new RegisterManualRecordingCommand(meetingId, callerId, s3Key, sizeBytes);
    }
}
