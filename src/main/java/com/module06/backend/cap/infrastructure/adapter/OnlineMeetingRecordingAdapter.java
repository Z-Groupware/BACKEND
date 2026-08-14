package com.module06.backend.cap.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.model.RecordingFilePolicy;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.port.out.OnlineMeetingRecordingPort;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingStorageUsagePort;

/* D의 MEET-18 녹음 확정 포트를 CAP recording·S3에 연결하는 어댑터다. */
@Component
@RequiredArgsConstructor
public class OnlineMeetingRecordingAdapter implements OnlineMeetingRecordingPort {

    private static final Logger log = LoggerFactory.getLogger(OnlineMeetingRecordingAdapter.class);
    private static final long CREATE_REVISION = 1L;

    private final RecordingRepository recordingRepository;
    private final CapObjectStoragePort capObjectStoragePort;
    private final ReportMeetingStorageUsagePort reportMeetingStorageUsagePort;

    @Override
    public void prepare(Preparation preparation) {
        if (preparation == null || preparation.companyId() == null || preparation.hostMemberId() == null) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_ID);
        }

        /* URL을 발급받은 회사·사용자의 pending 경로만 최종 회의에 연결할 수 있다. */
        String expectedPrefix = "recordings/org-%d/member-%d/online-pending/".formatted(
                preparation.companyId(), preparation.hostMemberId());
        String s3Key = preparation.s3Key();
        if (s3Key == null || s3Key.contains("..") || !s3Key.startsWith(expectedPrefix)
                || !s3Key.endsWith("/" + preparation.fileName())) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
        }

        try {
            /* Presign 단계의 입력을 신뢰하지 않고 최종 확정 시 형식·용량을 다시 검증한다. */
            RecordingFilePolicy.validate(
                    preparation.fileName(), preparation.contentType(), preparation.sizeBytes());

            /* S3 HEAD 결과가 요청 크기와 정확히 같아야 DB 녹음 메타를 저장한다. */
            if (!capObjectStoragePort.objectMatches(s3Key, preparation.sizeBytes())) {
                throw new BusinessException(CapErrorCode.CAP_RECORDING_NOT_UPLOADED);
            }
        } catch (RuntimeException exception) {
            deletePendingObjectBestEffort(s3Key, "최종 녹음 검증 실패");
            throw exception;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletePendingObjectBestEffort(s3Key, "회의 생성 트랜잭션 부재");
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        /* 커밋 실패 시 S3 객체까지 보상 삭제하고, 성공한 경우에만 STT·용량 집계를 시작한다. */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deletePendingObjectBestEffort(s3Key, "회의 생성 트랜잭션 롤백");
                }
            }
        });
    }

    @Override
    public void register(Registration registration) {
        if (registration == null || registration.companyId() == null || registration.hostMemberId() == null
                || registration.projectId() == null || registration.meetingId() == null) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_ID);
        }

        /* prepare와 동일한 인증 범위의 키만 recording으로 확정한다. */
        String expectedPrefix = "recordings/org-%d/member-%d/online-pending/".formatted(
                registration.companyId(), registration.hostMemberId());
        String s3Key = registration.s3Key();
        if (s3Key == null || !s3Key.startsWith(expectedPrefix)
                || !s3Key.endsWith("/" + registration.fileName())) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
        }

        /* meeting·agenda와 같은 트랜잭션에 recording 메타데이터를 저장한다. */
        recordingRepository.save(Recording.register(
                registration.meetingId(), registration.fileName(), s3Key, registration.sizeBytes(), true));

        /* DB 커밋이 끝난 뒤에만 저장 용량 집계를 시작한다. */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reportStorageBestEffort(registration);
            }
        });
    }

    private void reportStorageBestEffort(Registration registration) {
        try {
            reportMeetingStorageUsagePort.report(new ReportMeetingStorageUsageCommand(
                    registration.companyId(), registration.projectId(), registration.meetingId(),
                    registration.sizeBytes(), CREATE_REVISION));
        } catch (RuntimeException exception) {
            log.error("비대면 회의 저장 용량 기록 실패 — meetingId={}", registration.meetingId(), exception);
        }
    }

    private void deletePendingObjectBestEffort(String s3Key, String reason) {
        try {
            capObjectStoragePort.deleteRecording(s3Key);
        } catch (RuntimeException exception) {
            log.error("비대면 회의 pending 녹음 보상 삭제 실패 — reason={} s3Key={}", reason, s3Key, exception);
        }
    }
}
