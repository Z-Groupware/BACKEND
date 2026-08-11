package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.usecase.DeleteRecordingUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.ProcessingCompletionRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingStorageUsagePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

// 녹음 삭제(CAP-15): 회사 스코프 확인 → 녹음 존재 확인 → STT/분석 완료 차단 판정 → 오디오 하드 삭제.
// 지우는 건 오디오(recording 행·recording_part 조각·S3 파일)뿐 — utterance·action·벡터 등 분배된 데이터는 건드리지 않는다.
// 역할 게이트(owner/admin)는 컨트롤러 @PreAuthorize가, 회사 스코프는 여기서 검증한다.
@Service
@Transactional
public class DeleteRecordingService implements DeleteRecordingUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteRecordingService.class);

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CapMeetingAccessGuard accessGuard;
    private final RecordingRepository recordingRepository;
    private final RecordingPartRepository recordingPartRepository;
    private final ProcessingCompletionRepository processingCompletionRepository;
    private final CapObjectStoragePort capObjectStoragePort;
    private final ReportMeetingStorageUsagePort reportMeetingStorageUsagePort;
    private final CaptureUploadStateRepository captureUploadStateRepository;
    private final Clock clock;

    public DeleteRecordingService(MeetingReferenceRepository meetingReferenceRepository,
                                  CapMeetingAccessGuard accessGuard,
                                  RecordingRepository recordingRepository,
                                  RecordingPartRepository recordingPartRepository,
                                  ProcessingCompletionRepository processingCompletionRepository,
                                  CapObjectStoragePort capObjectStoragePort,
                                  ReportMeetingStorageUsagePort reportMeetingStorageUsagePort,
                                  CaptureUploadStateRepository captureUploadStateRepository,
                                  @Qualifier("meetingClock") Clock clock) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.accessGuard = accessGuard;
        this.recordingRepository = recordingRepository;
        this.recordingPartRepository = recordingPartRepository;
        this.processingCompletionRepository = processingCompletionRepository;
        this.capObjectStoragePort = capObjectStoragePort;
        this.reportMeetingStorageUsagePort = reportMeetingStorageUsagePort;
        this.captureUploadStateRepository = captureUploadStateRepository;
        this.clock = clock;
    }

    @Override
    public Result deleteRecording(Long meetingId, Long companyId, boolean confirm) {
        // 회의(따라서 녹음)가 없으면 404. 존재 여부를 회사 밖으로 노출하지 않도록 회사 스코프 전에 회의 유무만 본다.
        if (!meetingReferenceRepository.existsById(meetingId)) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_NOT_FOUND);
        }

        // 회사 스코프(403) — owner/admin이라도 다른 회사 녹음은 삭제할 수 없다.
        if (!accessGuard.isSameCompany(meetingId, companyId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }

        // 녹음본이 없으면 404(아직 조립/업로드 전이거나 이미 삭제됨).
        Recording recording = recordingRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_RECORDING_NOT_FOUND));

        // STT/분석이 안 끝났으면 confirm 없이는 차단(409) — 미확인 구간이 있는데 오디오를 지우면 재처리가 불가능해진다.
        if (!confirm && processingCompletionRepository.hasUnfinishedProcessing(meetingId, recording.isSttTriggered())) {
            throw new BusinessException(CapErrorCode.CAP_STT_NOT_CONFIRMED);
        }

        // 하드 삭제(되돌릴 수 없음): DB(recording_part → recording) 먼저, S3는 마지막.
        // DB와 S3는 하나의 트랜잭션으로 묶이지 않는다 — S3를 먼저 지우면 그 뒤 DB 삭제가 실패했을 때
        // 트랜잭션은 롤백되는데(recording 행이 복원) 파일은 이미 사라진 "고아 참조"가 남는다.
        // DB를 먼저 지우면 실패 방향이 반대로 뒤집힌다: DB 삭제 실패 → S3 호출 전이라 아무 일도 없고,
        // S3 삭제 실패 → 트랜잭션 전체가 롤백되어(DB도 되돌아감) 파일도 그대로 남아 재시도할 수 있다.
        long freedBytes = recording.getSizeBytes();
        recordingPartRepository.deleteByMeetingId(meetingId);
        recordingRepository.deleteByMeetingId(meetingId);
        captureUploadStateRepository.deleteByMeetingId(meetingId);
        capObjectStoragePort.deleteRecording(recording.getFileUrl());
        reportStorageUsageBestEffort(companyId, meetingId);

        return new Result(LocalDateTime.now(), freedBytes);
    }

    // metering에 0바이트로 report한다 — 실패해도 삭제 자체를 되돌리지 않는다
    // (CaptureUploadService.reportStorageUsageBestEffort와 동일 패턴).
    private void reportStorageUsageBestEffort(Long companyId, Long meetingId) {
        try {
            reportMeetingStorageUsagePort.report(new ReportMeetingStorageUsageCommand(
                    companyId, meetingId, 0L, clock.millis()));
        } catch (RuntimeException e) {
            log.error("저장 용량 미터링 기록 실패 — 삭제는 완료됨, 원장만 누락. meetingId={}", meetingId, e);
        }
    }
}
