package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.CompletePartUploadCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// complete 요청 body(JSON)를 그대로 받는 DTO. 컨트롤러의 path/header 값과 합쳐 Command로 변환한다.
public record CompletePartRequest(
        @NotNull Integer segmentSeq,
        @NotBlank String s3Key,
        @NotNull Long sizeBytes
) {

    public CompletePartUploadCommand toCommand(Long meetingId, Long callerId, int seq) {
        return new CompletePartUploadCommand(meetingId, callerId, segmentSeq, seq, s3Key, sizeBytes);
    }
}
