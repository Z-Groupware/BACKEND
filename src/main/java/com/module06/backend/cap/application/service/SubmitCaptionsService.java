package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.command.SubmitCaptionsCommand;
import com.module06.backend.cap.application.port.out.CaptionBroadcastPort;
import com.module06.backend.cap.application.usecase.SubmitCaptionsUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.CaptionChunkRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

// 자막 청크 배치 전송(CAP-11): 회의 존재 → 참석자 검증 → 배치 전체 유효성(rms 등) 검증 → 저장(재전송은
// 조용히 건너뜀) → 새로 저장된 조각만 브로드캐스트. 정본 아님 — 실시간 표시 + STT 실패 폴백용.
@Service
@Transactional
public class SubmitCaptionsService implements SubmitCaptionsUseCase {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CaptionChunkRepository captionChunkRepository;
    private final CaptionBroadcastPort captionBroadcastPort;

    public SubmitCaptionsService(MeetingReferenceRepository meetingReferenceRepository,
                                 CaptionChunkRepository captionChunkRepository,
                                 CaptionBroadcastPort captionBroadcastPort) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.captionChunkRepository = captionChunkRepository;
        this.captionBroadcastPort = captionBroadcastPort;
    }

    @Override
    public void submitCaptions(SubmitCaptionsCommand command) {
        // 인가: 회의 존재(404) → 참석자(403). 참석자 전원 가능 — Host 전용 아님.
        if (!meetingReferenceRepository.existsById(command.meetingId())) {
            throw new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND);
        }
        if (!meetingReferenceRepository.isAttendee(command.meetingId(), command.memberId())) {
            throw new BusinessException(CapErrorCode.CAP_NOT_ATTENDEE);
        }

        // 배치 전체를 먼저 도메인 객체로 변환한다 — 하나라도 유효하지 않으면(예: rms 누락) 여기서 예외가 터져
        // 저장을 하나도 하지 않은 상태로 배치 전체가 거절된다(부분 저장 없음, 422).
        List<CaptionChunk> chunks = command.chunks().stream()
                .map(chunk -> CaptionChunk.receive(command.meetingId(), command.memberId(), chunk.seq(),
                        chunk.startMs(), chunk.endMs(), chunk.text(), chunk.rms()))
                .toList();

        // 저장 — 이미 전송된 (meetingId, memberId, seq)는 재전송으로 보고 조용히 건너뛴다(멱등).
        List<CaptionChunk> newlySaved = captionChunkRepository.saveAllSkippingDuplicates(chunks);

        // 새로 저장된 조각만, 커밋 후에 브로드캐스트한다 — 커밋 전에 부르면 (1) 브로드캐스트가 던진 예외가
        // 저장까지 롤백시키고 (2) 커밋이 실패해도 구독자는 이미 못 받은 자막을 받게 된다.
        // 트랜잭션 동기화가 없는 컨텍스트(순수 단위 테스트)에서는 즉시 호출로 대체한다.
        if (!newlySaved.isEmpty()) {
            broadcastAfterCommit(command.meetingId(), newlySaved);
        }
    }

    private void broadcastAfterCommit(Long meetingId, List<CaptionChunk> newlySaved) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            captionBroadcastPort.broadcast(meetingId, newlySaved);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                captionBroadcastPort.broadcast(meetingId, newlySaved);
            }
        });
    }
}
