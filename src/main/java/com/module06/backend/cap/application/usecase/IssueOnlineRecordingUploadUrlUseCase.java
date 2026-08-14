package com.module06.backend.cap.application.usecase;

import com.module06.backend.cap.application.command.IssueOnlineRecordingUploadUrlCommand;

/* 비대면 회의 생성 전에 프론트가 S3로 직접 업로드할 주소를 발급하는 인바운드 포트다. */
public interface IssueOnlineRecordingUploadUrlUseCase {

    Result issueOnlineRecordingUploadUrl(IssueOnlineRecordingUploadUrlCommand command);

    record Result(String s3Key, String presignedUrl, int expiresInSeconds) {
    }
}
