package com.module06.backend.cap.application.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.meeting.application.event.MeetingCompletionRequestedEvent;

/*
 * MEET-08(회의 종료)이 발행한 이벤트를 받아 녹음 조립을 시작한다 — CAP-05(수동 "녹음 종료" API)의
 * 자동 경로다. capture.MeetingCompletedAnalysisTrigger와 같은 자리(같은 이벤트, AFTER_COMMIT,
 * 전용 스레드 풀)에 있지만, 이 도메인의 이벤트를 대신 부르지 않는다는 원칙은 그대로다 — 이 클래스는
 * MeetingCompletionRequestedEvent 레코드 하나만 안다.
 *
 * <h2>CAP-05를 대체하지 않는다</h2>
 * API 명세에 이미 "이 API는 호출 안 해도 된다 — 대부분의 회의는 종료 시 자동 조립되므로 선택적"
 * 이라고 적혀 있다. 이 트리거가 그 자동 경로고, CAP-05는 이게 실패했을 때 사람이 쓰는 대체 수단으로
 * 남는다 — 그래서 CAP-05 엔드포인트/서비스는 건드리지 않는다.
 *
 * <h2>클라이언트가 주장한 값이 아니라 우리 DB를 신뢰한다</h2>
 * CAP-05는 lastSegmentSeq/lastSeq를 요청 본문(클라 주장)으로 받지만, 여기는 호출자가 없다 —
 * CaptureUploadState에 실제로 쌓인 값을 그대로 쓴다.
 *
 * <h2>이중 조립 방지</h2>
 * CAP-10(수동 녹음 업로드, ManualRecordingService)이 이미 같은 문제를 풀어놨다 —
 * recording 테이블에 이 회의 행이 있으면(수동 업로드든, 이미 끝난 자동/수동 조립이든) 새로
 * 트리거하지 않는다. 별도 CAS 플래그를 새로 만들 필요가 없다.
 *
 * <h2>여기서 던지지 않는다</h2>
 * MeetingCompletedAnalysisTrigger와 같은 이유다 — AFTER_COMMIT 리스너의 예외는 이미 커밋된
 * 트랜잭션을 되돌리지 못하고, 비동기 스레드에서는 요청자에게 닿지도 않는다. 실패는 로그로 남기고,
 * 사람이 CAP-05로 다시 트리거한다.
 */
@Component
public class MeetingCompletedAssemblyTrigger {

    private static final Logger log = LoggerFactory.getLogger(MeetingCompletedAssemblyTrigger.class);

    private final RecordingRepository recordingRepository;
    private final CaptureUploadStateRepository captureUploadStateRepository;
    private final RecordingGapChecker gapChecker;
    private final RecordingAssemblyDispatcher recordingAssemblyDispatcher;
    private final SttBlockCutTrigger sttBlockCutTrigger;

    public MeetingCompletedAssemblyTrigger(RecordingRepository recordingRepository,
                                           CaptureUploadStateRepository captureUploadStateRepository,
                                           RecordingGapChecker gapChecker,
                                           RecordingAssemblyDispatcher recordingAssemblyDispatcher,
                                           SttBlockCutTrigger sttBlockCutTrigger) {
        this.recordingRepository = recordingRepository;
        this.captureUploadStateRepository = captureUploadStateRepository;
        this.gapChecker = gapChecker;
        this.recordingAssemblyDispatcher = recordingAssemblyDispatcher;
        this.sttBlockCutTrigger = sttBlockCutTrigger;
    }

    @Async("recordingAssemblyTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingCompleted(MeetingCompletionRequestedEvent event) {
        Long companyId = event.companyId();
        Long meetingId = event.meetingId();

        try {
            if (recordingRepository.existsByMeetingId(meetingId)) {
                log.info("이미 등록된 녹음본이 있어 자동 조립을 생략한다 — meetingId={}", meetingId);
                return;
            }

            Optional<CaptureUploadState> state = captureUploadStateRepository.findByMeetingId(meetingId);
            if (state.isEmpty()) {
                log.info("녹음이 시작된 적 없는 회의라 자동 조립을 생략한다 — meetingId={}", meetingId);
                return;
            }

            int lastSegmentSeq = state.get().getSegmentSeq();
            int lastSeq = state.get().getLastSeq();
            if (lastSegmentSeq == 0 && lastSeq == 0) {
                log.info("업로드된 청크가 없어 자동 조립을 생략한다 — meetingId={}", meetingId);
                return;
            }

            if (gapChecker.hasGap(meetingId, lastSegmentSeq, lastSeq)) {
                log.warn("녹음에 빠진 순번이 있어 자동 조립을 생략한다 — meetingId={}. "
                        + "CAP-05(수동 녹음 종료)로 상태를 확인해야 한다.", meetingId);
                return;
            }

            // 조립(parts 삭제)이 청크를 지우기 전에, 마지막 세그먼트의 자투리를 TAIL STT 블록으로
            // 먼저 마무리한다(동기 호출 — SttBlockCutTrigger.finalizeTailBlockOnMeetingCompletion
            // 주석 참고). 안 하면 그 구간이 STT 블록 자체가 안 생겨 분석이 빠진 채로 조용히 돈다.
            sttBlockCutTrigger.finalizeTailBlockOnMeetingCompletion(companyId, meetingId, lastSegmentSeq, lastSeq,
                    state.get().getBlocksFormed(), state.get().getLastBlockEndOffsetMs());

            recordingAssemblyDispatcher.dispatch(meetingId, lastSegmentSeq, lastSeq);
            log.info("회의 종료 자동 조립 트리거 — meetingId={} lastSegmentSeq={} lastSeq={}",
                    meetingId, lastSegmentSeq, lastSeq);

        } catch (RuntimeException e) {
            // 던져봐야 갈 곳이 없다(클래스 주석). 사람이 CAP-05로 다시 트리거할 수 있다.
            log.error("회의 종료 자동 조립 실패 — meetingId={}", meetingId, e);
        }
    }
}
