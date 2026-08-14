package com.module06.backend.cap.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import com.module06.backend.cap.application.command.IssueOnlineRecordingUploadUrlCommand;
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.usecase.IssueOnlineRecordingUploadUrlUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.RecordingFilePolicy;
import com.module06.backend.global.exception.BusinessException;

/* MEET-18 생성 전에 녹음 파일을 백엔드를 거치지 않고 S3에 올릴 URL을 발급한다. */
@Service
@RequiredArgsConstructor
public class OnlineRecordingUploadUrlService implements IssueOnlineRecordingUploadUrlUseCase {

    private final CapObjectStoragePort capObjectStoragePort;

    @Override
    public Result issueOnlineRecordingUploadUrl(IssueOnlineRecordingUploadUrlCommand command) {
        if (command == null || command.companyId() == null || command.memberId() == null) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_ID);
        }
        String fileName = RecordingFilePolicy.validate(
                command.fileName(), command.contentType(), command.sizeBytes());

        /* 회사·업로더·난수 경로를 묶어 다른 사용자의 임시 녹음 키를 재사용하지 못하게 한다. */
        String s3Key = "recordings/org-%d/member-%d/online-pending/%s/%s".formatted(
                command.companyId(), command.memberId(), UUID.randomUUID(), fileName);
        CapObjectStoragePort.IssuedPartUploadUrl issued =
                capObjectStoragePort.issuePartUploadUrl(s3Key, command.contentType());
        return new Result(s3Key, issued.presignedUrl(), issued.expiresInSeconds());
    }
}
