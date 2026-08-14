package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.command.StartRecordingAssemblyCommand;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

// 녹음 종료/조립(CAP-05): 회의 존재 → 참석자 → 이중 조립 방지 → 녹음자 검증 → 세그먼트별 seq 연속성
// 검증 → 조립 트리거. 조립은 되돌릴 수 없으므로(성공 시 parts 삭제), 중간에 구멍이 있으면 시작 전에
// 409로 막는다.
@Service
@Transactional
public class RecordingAssemblyService implements StartRecordingAssemblyUseCase {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CapMeetingAccessGuard accessGuard;
    private final RecordingRepository recordingRepository;
    private final CaptureUploadStateRepository captureUploadStateRepository;
    private final RecordingGapChecker gapChecker;
    private final RecordingAssemblyDispatcher recordingAssemblyDispatcher;
    private final SttBlockCutTrigger sttBlockCutTrigger;

    public RecordingAssemblyService(MeetingReferenceRepository meetingReferenceRepository,
                                    CapMeetingAccessGuard accessGuard,
                                    RecordingRepository recordingRepository,
                                    CaptureUploadStateRepository captureUploadStateRepository,
                                    RecordingGapChecker gapChecker,
                                    RecordingAssemblyDispatcher recordingAssemblyDispatcher,
                                    SttBlockCutTrigger sttBlockCutTrigger) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.accessGuard = accessGuard;
        this.recordingRepository = recordingRepository;
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

        // 이중 조립 방지 — MeetingCompletedAssemblyTrigger(자동 경로)의 existsByMeetingId 선검사와
        // 동일 취지(그 클래스 주석이 이걸 도메인 전체의 조립 가드로 제시한다). 이 수동 경로(CAP-05)만
        // 이 검사가 빠져 있으면, 두 번째 호출이 gap 검사까지 통과해 첫 번째 조립이 지운 parts를
        // 다시 조립 시도하거나(자료 없음) 중복 ffmpeg 작업을 또 디스패치할 수 있다 — recording 저장
        // 시점의 UNIQUE 제약(V4.2.1)은 그때는 이미 parts가 삭제된 뒤라 너무 늦다.
        if (recordingRepository.existsByMeetingId(command.meetingId())) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_ALREADY_SUBMITTED);
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
        //
        // 커밋 후에만 트리거한다(CaptureUploadService.finalizeTailBlockAfterCommit와 동일 이유) —
        // 이 메서드 전체가 @Transactional이라, 커밋 전에 dispatch하면 @Async 워커가 다른 커넥션의
        // 별도 트랜잭션으로 즉시 실행을 시작할 수 있다. 그러면 방금 위에서 finalizeTailBlockOn
        // MeetingCompletion이 반영한 TAIL 블록 상태를, 아직 커밋 안 된 이 트랜잭션 밖에서는 못 본
        // 채로 조립이 parts를 읽어버릴 수 있다.
        dispatchAssemblyAfterCommit(command.meetingId(), command.lastSegmentSeq(), command.lastSeq());

        // 여기 도달했으면 구멍이 없으므로 missingSeqs는 빈 목록.
        return new Result("ASSEMBLING", List.of());
    }

    // CaptureUploadService.finalizeTailBlockAfterCommit와 동일 패턴 — 트랜잭션 동기화가 없는 컨텍스트
    // (순수 단위 테스트)에서는 즉시 호출로 대체한다.
    private void dispatchAssemblyAfterCommit(Long meetingId, int lastSegmentSeq, int lastSeq) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recordingAssemblyDispatcher.dispatch(meetingId, lastSegmentSeq, lastSeq);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recordingAssemblyDispatcher.dispatch(meetingId, lastSegmentSeq, lastSeq);
            }
        });
    }

    private boolean isOutOfRange(int value) {
        return value < 0 || value > CaptureUploadState.MAX_SEQ;
    }
}
