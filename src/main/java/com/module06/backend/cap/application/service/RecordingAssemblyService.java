package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.command.StartRecordingAssemblyCommand;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.RecordingAssemblyPort;
import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 녹음 종료/조립(CAP-05): 회의 존재 → 참석자 → 녹음자 검증 → 세그먼트별 seq 연속성 검증 → 조립 트리거.
// 조립은 되돌릴 수 없으므로(성공 시 parts 삭제), 중간에 구멍이 있으면 시작 전에 409로 막는다.
@Service
@Transactional
public class RecordingAssemblyService implements StartRecordingAssemblyUseCase {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CapMeetingAccessGuard accessGuard;
    private final CaptureUploadStateRepository captureUploadStateRepository;
    private final RecordingGapChecker gapChecker;
    private final RecordingAssemblyPort recordingAssemblyPort;

    public RecordingAssemblyService(MeetingReferenceRepository meetingReferenceRepository,
                                    CapMeetingAccessGuard accessGuard,
                                    CaptureUploadStateRepository captureUploadStateRepository,
                                    RecordingGapChecker gapChecker,
                                    RecordingAssemblyPort recordingAssemblyPort) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.accessGuard = accessGuard;
        this.captureUploadStateRepository = captureUploadStateRepository;
        this.gapChecker = gapChecker;
        this.recordingAssemblyPort = recordingAssemblyPort;
    }

    @Override
    public Result startRecordingAssembly(StartRecordingAssemblyCommand command) {
        // 범위 검증(DoS 방어) — 본문 lastSegmentSeq/lastSeq가 과대하면 연속성 순회가 폭증하므로 먼저 거른다.
        if (isOutOfRange(command.lastSegmentSeq()) || isOutOfRange(command.lastSeq())) {
            throw new BusinessException(CapErrorCode.CAP_INVALID_SEQ);
        }

        // 인가: 회의 존재(404) → 참석자(403) → 상태행 → 녹음자(403). CAP-08과 동일한 회의 접근 확인.
        if (!meetingReferenceRepository.existsById(command.meetingId())) {
            throw new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND);
        }
        if (!accessGuard.isAttendee(command.meetingId(), command.callerId())) {
            throw new BusinessException(CapErrorCode.CAP_NOT_ATTENDEE);
        }
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(command.meetingId())
                .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_NOT_CURRENT_RECORDER));
        state.verifyRecorder(command.callerId());

        // seq 연속성 검증 — 구멍이 하나라도 있으면 조립을 시작하지 않고 409로 막는다(어느 순번인지는 CAP-08로 확인).
        if (gapChecker.hasGap(command.meetingId(), command.lastSegmentSeq(), command.lastSeq())) {
            throw new BusinessException(CapErrorCode.CAP_ASSEMBLY_INCOMPLETE);
        }

        // 연속성 OK → 조립 파이프라인 트리거(스텁, best-effort). 실제 조립/상태 전이는 파이프라인이 담당.
        recordingAssemblyPort.startAssembly(command.meetingId(), command.lastSegmentSeq(), command.lastSeq());

        // 여기 도달했으면 구멍이 없으므로 missingSeqs는 빈 목록.
        return new Result("ASSEMBLING", List.of());
    }

    private boolean isOutOfRange(int value) {
        return value < 0 || value > CaptureUploadState.MAX_SEQ;
    }
}
