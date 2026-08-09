package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import com.module06.backend.cap.application.usecase.RegisterManualRecordingUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 수동 녹음 업로드(CAP-10): 회의 존재 → Host 검증 → s3Key 검증 → 중복 제출 확인 → recording 등록 → 단일 블록 STT 트리거.
// [녹음] 버튼 대신 외부(온라인 회의 등)에서 녹음한 파일을 직접 첨부하는 대체 경로다. 업로드 자체는 별도 presigned 절차이고,
// 이 API는 완료된 파일의 메타데이터 등록 + STT 트리거만 담당한다.
@Service
@Transactional
public class ManualRecordingService implements RegisterManualRecordingUseCase {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CapMeetingAccessGuard accessGuard;
    private final RecordingRepository recordingRepository;
    private final MeetingRecordingSttPort meetingRecordingSttPort;

    public ManualRecordingService(MeetingReferenceRepository meetingReferenceRepository,
                                  CapMeetingAccessGuard accessGuard,
                                  RecordingRepository recordingRepository,
                                  MeetingRecordingSttPort meetingRecordingSttPort) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.accessGuard = accessGuard;
        this.recordingRepository = recordingRepository;
        this.meetingRecordingSttPort = meetingRecordingSttPort;
    }

    @Override
    public Result registerManualRecording(RegisterManualRecordingCommand command) {
        // 회의 존재 확인(404) + companyId 확보(키 검증용).
        Long companyId = meetingReferenceRepository.findCompanyId(command.meetingId())
                .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND));

        // Host만 가능(403). 참석자보다 좁은 권한 — 회의 담당자 본인만.
        if (!accessGuard.isHost(command.meetingId(), command.callerId())) {
            throw new BusinessException(CapErrorCode.CAP_NOT_HOST);
        }

        // s3Key는 서버가 기대하는 영구 보관 경로 접두(recordings/org-{orgId}/meeting-{meetingId}/)로 시작해야 한다 —
        // 타 회사·타 회의·임시(stt-temp) 경로나 경로 조작(..)을 막는다(PR1 complete의 키 검증과 동일 취지).
        String expectedPrefix = "recordings/org-%d/meeting-%d/".formatted(companyId, command.meetingId());
        String s3Key = command.s3Key();
        if (s3Key == null || s3Key.contains("..") || !s3Key.startsWith(expectedPrefix)) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
        }
        String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
        if (fileName.isBlank()) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
        }

        // 이미 제출된 녹음이 있으면 중복 제출 거부(409).
        if (recordingRepository.existsByMeetingId(command.meetingId())) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_ALREADY_SUBMITTED);
        }

        // 메타 등록(durationSec은 파이프라인이 async로 채움) + 단일 블록 STT 트리거(스텁).
        // sttTriggered=true로 등록 — 트리거 직후엔 stt_block이 아직 0건이라, CAP-15 삭제 차단 판정이
        // 이를 "완료"가 아니라 "진행 중"으로 읽게 한다.
        recordingRepository.save(Recording.register(command.meetingId(), fileName, s3Key, command.sizeBytes(), true));
        meetingRecordingSttPort.triggerWholeFileStt(command.meetingId(), s3Key);

        // durationMs=0(파이프라인이 채움), status="DONE"(제출=완료 리터럴, meeting.status는 D 소유라 쓰지 않음).
        return new Result(command.meetingId(), 0L, command.sizeBytes(), "DONE");
    }
}
