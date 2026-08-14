package com.module06.backend.cap.application.command;

// 수동 녹음 업로드용 presigned URL 발급 명령 — 컨트롤러가 path/토큰/본문을 합쳐 만든다.
// callerId는 헤더가 아니라 JWT principal에서 꺼낸 값이다(남의 id로 Host 행세 차단).
public record IssueManualRecordingUploadUrlCommand(
        Long meetingId,
        Long callerId,
        String fileName,
        String contentType
) {
}
