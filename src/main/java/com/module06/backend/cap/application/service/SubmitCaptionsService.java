package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.command.SubmitCaptionsCommand;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.CaptionBroadcastPort;
import com.module06.backend.cap.application.usecase.SubmitCaptionsUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.CaptionChunkRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.ReportMeetingTextStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingTextStorageUsagePort;
import com.module06.backend.metering.domain.model.TextStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.List;

// 자막 청크 배치 전송(CAP-11): 회의 존재 → host 검증 → 배치 전체 유효성(rms 등) 검증 → 저장(재전송은
// 조용히 건너뜀) → 새로 저장된 조각만 브로드캐스트. 정본 아님 — 실시간 표시 + STT 실패 폴백용.
//
// host 전용인 이유 — 녹음이 host 한 명의 기기로만 이뤄지기로 확정되면서(좁은 회의실 음성 섞임
// 문제), 화자를 여러 참석자 중에서 rms로 가려내는 전제 자체가 성립하지 않게 됐다(capture
// SpeakerAttributionResolver와 함께 정리). memberId 필드 자체는 남아있지만(멱등 키·발신자 표시용),
// 이제 그 값은 항상 host 자신이므로 참석자 전원에게 제출을 열어둘 이유가 없다.
@Service
@Transactional
public class SubmitCaptionsService implements SubmitCaptionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitCaptionsService.class);

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CapMeetingAccessGuard accessGuard;
    private final CaptionChunkRepository captionChunkRepository;
    private final CaptionBroadcastPort captionBroadcastPort;
    private final ReportMeetingTextStorageUsagePort reportMeetingTextStorageUsagePort;

    public SubmitCaptionsService(MeetingReferenceRepository meetingReferenceRepository,
                                 CapMeetingAccessGuard accessGuard,
                                 CaptionChunkRepository captionChunkRepository,
                                 CaptionBroadcastPort captionBroadcastPort,
                                 ReportMeetingTextStorageUsagePort reportMeetingTextStorageUsagePort) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.accessGuard = accessGuard;
        this.captionChunkRepository = captionChunkRepository;
        this.captionBroadcastPort = captionBroadcastPort;
        this.reportMeetingTextStorageUsagePort = reportMeetingTextStorageUsagePort;
    }

    @Override
    public void submitCaptions(SubmitCaptionsCommand command) {
        // 인가: 회의 존재(404) → host(403). 녹음자와 동일하게 host만 자막을 보낼 수 있다.
        if (!meetingReferenceRepository.existsById(command.meetingId())) {
            throw new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND);
        }
        if (!accessGuard.isHost(command.meetingId(), command.memberId())) {
            throw new BusinessException(CapErrorCode.CAP_NOT_HOST);
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
            // 전부 재전송(중복)이었으면 caption_chunk가 안 늘어나므로 다시 잴 이유가 없다 — 새로
            // 저장된 게 있을 때만 재계산한다.
            reportCaptionStorageUsageBestEffort(command.meetingId());
        }
    }

    // 저장소 관리 화면(/manage/storage)의 자막 용량 집계용 — 이 회의 caption_chunk 전체를 다시 읽어
    // 합산한 뒤 metering에 리포트한다. 새 쿼리 없이 기존 findByMeetingId를 쓴다(CAP-12가 이미 쓰는
    // 메서드) — meeting_storage_usage의 회사 합산도 SQL SUM이 아니라 Java 스트림 합산이라 동일하게
    // 맞췄다. 실패해도 자막 저장·브로드캐스트 자체는 되돌리지 않는다(원장만 누락).
    private void reportCaptionStorageUsageBestEffort(Long meetingId) {
        try {
            Long companyId = meetingReferenceRepository.findCompanyId(meetingId)
                    .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND));
            Long projectId = meetingReferenceRepository.findProjectId(meetingId)
                    .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND));
            long captionBytes = captionChunkRepository.findByMeetingId(meetingId).stream()
                    .mapToLong(chunk -> chunk.getText().getBytes(StandardCharsets.UTF_8).length)
                    .sum();
            reportMeetingTextStorageUsagePort.report(new ReportMeetingTextStorageUsageCommand(
                    companyId, projectId, meetingId, TextStorageSource.CAPTION, captionBytes,
                    System.currentTimeMillis()));
        } catch (RuntimeException e) {
            log.error("자막 저장 용량 미터링 기록 실패 — 자막 저장은 완료됨, 원장만 누락. meetingId={}", meetingId, e);
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
