package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.command.StartRecordingAssemblyCommand;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
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
    private final RecordingAssemblyDispatcher recordingAssemblyDispatcher;
    private final SttBlockCutTrigger sttBlockCutTrigger;

    public RecordingAssemblyService(MeetingReferenceRepository meetingReferenceRepository,
                                    CapMeetingAccessGuard accessGuard,
                                    CaptureUploadStateRepository captureUploadStateRepository,
                                    RecordingGapChecker gapChecker,
                                    RecordingAssemblyDispatcher recordingAssemblyDispatcher,
                                    SttBlockCutTrigger sttBlockCutTrigger) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.accessGuard = accessGuard;
        this.captureUploadStateRepository = captureUploadStateRepository;
        this.gapChecker = gapChecker;
        this.recordingAssemblyDispatcher = recordingAssemblyDispatcher;
        this.sttBlockCutTrigger = sttBlockCutTrigger;
    }

    @Override
    public Result startRecordingAssembly(StartRecordingAssemblyCommand command) {
        // 범위 검증(DoS 방어) — 본문 lastSegmentSeq/lastSeq가 과대하면 연속성 순회가 폭증하므로 먼저 거른다.
        if (isOutOfRange(command.lastSegmentSeq()) || isOutOfRange(command.lastSeq())) {
            throw new BusinessException(CapErrorCode.CAP_INVALID_SEQ);
        }

        // 인가: 회의 존재(404) → 참석자(403) → 상태행 → 녹음자(403). CAP-08과 동일한 회의 접근 확인.
        Long companyId = meetingReferenceRepository.findCompanyId(command.meetingId())
                .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND));
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

        // 조립(parts 삭제)이 청크를 지우기 전에, 마지막 세그먼트의 자투리를 TAIL STT 블록으로
        // 먼저 마무리한다(동기 호출 — MeetingCompletedAssemblyTrigger의 자동 경로와 동일 이유·
        // 동일 메서드). CAP-05는 자동 경로가 실패했을 때 사람이 쓰는 대체 수단이라, 실패를 삼키고
        // 조립을 진행하면 사람이 재시도해도 같은 콘텐츠 유실이 반복된다(CodeRabbit 지적) — 409로
        // 알려서 클라이언트가 다시 호출하게 한다.
        boolean tailFinalized = sttBlockCutTrigger.finalizeTailBlockOnMeetingCompletion(companyId,
                command.meetingId(), command.lastSegmentSeq(), command.lastSeq(), state.getBlocksFormed(),
                state.getLastBlockEndOffsetMs());
        if (!tailFinalized) {
            throw new BusinessException(CapErrorCode.CAP_ASSEMBLY_INCOMPLETE);
        }

        // 연속성 OK → 조립 파이프라인 트리거(best-effort, 비동기). 실제 조립/상태 전이는 파이프라인이 담당.
        // 여기서 바로 포트를 부르지 않는다 — 실 어댑터는 무거운 ffmpeg 작업이라, 동기로 부르면
        // 이 API의 "즉시 202" 계약이 깨진다(RecordingAssemblyDispatcher 주석 참고).
        recordingAssemblyDispatcher.dispatch(command.meetingId(), command.lastSegmentSeq(), command.lastSeq());

        // 여기 도달했으면 구멍이 없으므로 missingSeqs는 빈 목록.
        return new Result("ASSEMBLING", List.of());
    }

    private boolean isOutOfRange(int value) {
        return value < 0 || value > CaptureUploadState.MAX_SEQ;
    }
}
