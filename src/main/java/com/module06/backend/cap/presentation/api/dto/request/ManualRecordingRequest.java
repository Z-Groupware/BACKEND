package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;
import com.module06.backend.cap.domain.model.RecordingFilePolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 수동 녹음 업로드 요청 body. 컨트롤러의 path/토큰과 합쳐 Command로 변환한다.
//
// [HTTP 계약 변경] fileName 필드가 새로 필수로 추가됐다(2026-08-14, presign S3 키 한글/공백
// 서명 오류(403) 수정) — s3Key가 이제 UUID+확장자라 원본 파일명을 더 이상 담지 않는다.
// FE는 s3Key에 원본 파일명을 묻어 보내던 방식을 버리고, 원본 파일명을 이 필드로 별도로
// 같이 보내야 한다. 커밋/PR 올릴 때 FE와 반드시 맞출 것.
public record ManualRecordingRequest(
        @NotBlank String s3Key,
        @NotBlank String fileName,
        @NotNull @Positive @Max(RecordingFilePolicy.MAX_SIZE_BYTES) Long sizeBytes
) {

    public RegisterManualRecordingCommand toCommand(Long meetingId, Long callerId) {
        return new RegisterManualRecordingCommand(meetingId, callerId, s3Key, fileName, sizeBytes);
    }
}
