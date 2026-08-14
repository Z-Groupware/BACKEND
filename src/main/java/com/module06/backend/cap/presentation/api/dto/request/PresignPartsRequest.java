package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.IssuePartUploadUrlsCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

// presign 요청 body(JSON)를 그대로 받는 DTO. 컨트롤러의 path/header 값과 합쳐 Command로 변환한다.
public record PresignPartsRequest(
        @Min(1) @Max(PresignPartsRequest.MAX_COUNT) Integer count,
        @NotBlank String contentType
) {
    private static final int DEFAULT_COUNT = 20; // 15초×20=5분치 (문서 기본값)

    // 배치 1회 상한 — 15초×100=25분치. RecordingAssemblyService.isOutOfRange와 같은 이유의
    // DoS 방어다(CodeRabbit 지적) — 상한이 없으면 요청 하나로 count번 서명 연산을 시켜 응답을
    // 부풀릴 수 있다. 문서 기본값(20, 5분치)의 다음 배치 요청 전에 소진되는 정상 흐름을 넉넉히
    // 감안해 5배로 잡았다.
    static final int MAX_COUNT = 100;

    public IssuePartUploadUrlsCommand toCommand(Long meetingId, Long callerId) {
        return new IssuePartUploadUrlsCommand(meetingId, callerId,
                count != null ? count : DEFAULT_COUNT, contentType);
    }
}
