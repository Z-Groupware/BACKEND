package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.CompletePartUploadCommand;
import com.module06.backend.cap.domain.model.RecordingPart;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// complete 요청 body(JSON)를 그대로 받는 DTO. 컨트롤러의 path/header 값과 합쳐 Command로 변환한다.
//
// sizeBytes에 @Positive·@Max를 두는 이유(ManualRecordingRequest와 대칭) — 이 값 없이도
// RecordingPart 생성자가 결국 같은 상한(MAX_SIZE_BYTES)을 검증하긴 하지만, 그건 objectMatches
// S3 HEAD 호출을 거친 뒤 CompletePartUploadWriter 안쪽에서야 일어난다. 여기서 먼저 걸러야 잘못된
// 값이 S3 네트워크 호출까지 가지 않고 요청 경계에서 바로 400으로 끝난다.
public record CompletePartRequest(
        @NotNull Integer segmentSeq,
        @NotBlank String s3Key,
        @NotNull @Positive @Max(RecordingPart.MAX_SIZE_BYTES) Long sizeBytes
) {

    public CompletePartUploadCommand toCommand(Long meetingId, Long callerId, int seq) {
        return new CompletePartUploadCommand(meetingId, callerId, segmentSeq, seq, s3Key, sizeBytes);
    }
}
