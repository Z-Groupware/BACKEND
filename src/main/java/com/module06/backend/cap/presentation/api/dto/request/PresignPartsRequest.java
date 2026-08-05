package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.IssuePartUploadUrlsCommand;
import jakarta.validation.constraints.NotBlank;

// presign 요청 body(JSON)를 그대로 받는 DTO. 컨트롤러의 path/header 값과 합쳐 Command로 변환한다.
public record PresignPartsRequest(
        Integer count,
        @NotBlank String contentType
) {
    private static final int DEFAULT_COUNT = 20; // 15초×20=5분치 (문서 기본값)

    public IssuePartUploadUrlsCommand toCommand(Long meetingId, Long callerId) {
        return new IssuePartUploadUrlsCommand(meetingId, callerId,
                count != null ? count : DEFAULT_COUNT, contentType);
    }
}
