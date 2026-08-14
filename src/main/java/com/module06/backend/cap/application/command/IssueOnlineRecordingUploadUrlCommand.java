package com.module06.backend.cap.application.command;

/* 비대면 회의 생성 전 S3 직접 업로드 URL 발급에 필요한 인증·파일 메타데이터다. */
public record IssueOnlineRecordingUploadUrlCommand(
        Long companyId,
        Long memberId,
        String fileName,
        String contentType,
        Long sizeBytes
) {
}
