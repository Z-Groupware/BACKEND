package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 수동 녹음 업로드 요청 body. 컨트롤러의 path/토큰과 합쳐 Command로 변환한다.
public record ManualRecordingRequest(
        @NotBlank String s3Key,
        @NotNull @Positive @Max(ManualRecordingRequest.MAX_SIZE_BYTES) Long sizeBytes
) {

    // 5GiB — CapS3ObjectStorageAdapter가 발급하는 건 단일 presigned PUT이지 멀티파트가 아니라서,
    // 이게 S3 자체의 하드 리밋이다(AWS 문서: PutObject 단일 업로드 최대 5GiB). 이 값보다 큰 파일은
    // 이 presign 메커니즘으로 애초에 업로드가 안 되므로, 상한 없이 클라이언트가 주장한 sizeBytes를
    // 그대로 받으면 (청크 경로의 RecordingPart.MAX_SIZE_BYTES와 달리) 어디서도 걸러지지 않는다.
    static final long MAX_SIZE_BYTES = 5L * 1024 * 1024 * 1024;

    public RegisterManualRecordingCommand toCommand(Long meetingId, Long callerId) {
        return new RegisterManualRecordingCommand(meetingId, callerId, s3Key, sizeBytes);
    }
}
