package com.module06.backend.cap.application.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.port.out.SttBlockAudioAssemblyPort;
import com.module06.backend.cap.application.port.out.SttBlockCutDetectionPort;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.capture.application.port.in.CreateSttBlockPort;
import com.module06.backend.capture.application.port.in.CreateSttBlockPort.CreateSttBlockCommand;

/*
 * 10분/40청크 자동 블록 트리거(CAP-07 처리 정책) — 청크가 40개(=10분) 쌓일 때마다 AI-01로
 * 무음 절단 지점을 찾고, 블록 오디오를 조립하고, capture(STT 도메인)에 블록 생성·제출을 요청한다.
 *
 * <h2>왜 비동기인가</h2>
 * ffmpeg 변환·AI-01 호출·블록 조립은 청크 완료 통보(CAP-07)가 "즉시 반환한다"는 명세 계약과
 * 안 맞는 무거운 작업이다(MeetingCompletedAnalysisTrigger와 같은 이유 — 전용 스레드 풀로 뺀다).
 *
 * <h2>무거운 작업 전에 블록 순번부터 예약한다(CodeRabbit 지적)</h2>
 * 처음엔 "조립·제출까지 다 끝난 뒤에 blocksFormed를 올리자"고 짰는데, 그러면 그 무거운 작업이
 * 도는 몇 초 사이에 다음 청크가 들어와 같은 트리거가 한 번 더 돌 수 있고, 둘 다 같은 blockSeq를
 * 계산해서 **블록이 두 번 만들어지고 Transcribe에도 두 번 제출**될 수 있었다. 그래서 무거운 작업을
 * 시작하기 **전에** {@link CaptureUploadStateRepository#tryReserveNextBlockSeq}로 이 블록 자리를
 * 먼저 원자적으로 찜한다 — 경합에서 지면(다른 트리거가 이미 예약했으면) 그냥 이번 회차를
 * 건너뛴다.
 *
 * <h2>세그먼트를 넘어 오디오를 이어붙이지 않는다(단순화 결정)</h2>
 * 녹음자 이어받기로 세그먼트가 바뀌기 직전엔 {@link #finalizeTailBlockOnSegmentChange}가 그때까지
 * 쌓인 자투리를 TAIL 블록으로 마무리한다. 새 세그먼트의 청크 순번·블록 끝 지점 리셋은
 * {@code CaptureUploadState.assignOrVerifyRecorder}가 세그먼트 전환과 같은 순간(동기)에 이미
 * 해두므로, 여기서는 예약된 TAIL 블록 하나를 만드는 것만 신경 쓰면 된다. 그래도 이전 세그먼트의
 * 오디오와 새 세그먼트의 오디오를 하나로 이어붙이는 연속성 처리는 안 한다.
 *
 * <h2>여기서 던지지 않는다(단, TAIL 마무리는 다르다)</h2>
 * triggerIfThresholdReached는 best-effort다. 예약까지 성공한 뒤 실패해도 청크 업로드 자체(CAP-07)는
 * 이미 성공적으로 끝난 뒤라 되돌릴 것이 없다 — block_seq에 빈 번호 하나가 남을 뿐(해롭지 않다),
 * 다음 트리거가 같은 자리를 다시 경합하는 것보다 안전하다.
 * 반면 finalizeTailBlockOnMeetingCompletion은 실패 여부를 boolean으로 호출자에게 돌려준다
 * (CodeRabbit 지적) — 조립이 곧 recording_part/S3 청크를 지우므로, TAIL 마무리가 실패한 채 조립이
 * 진행되면 그 자투리 구간은 영영 복구할 수 없다. 예약 경합·조립 실패 모두 "실패"로 취급해 호출자가
 * 조립을 미루게 한다.
 */
@Component
public class SttBlockCutTrigger {

    private static final Logger log = LoggerFactory.getLogger(SttBlockCutTrigger.class);

    // 청크 하나는 15초 — 40개 누적이 곧 10분이다(MAX_SEQ 산정과 같은 물리적 사실).
    private static final long CHUNK_DURATION_MS = 15_000L;
    private static final int CHUNKS_PER_BLOCK = 40;
    private static final long BLOCK_DURATION_MS = CHUNKS_PER_BLOCK * CHUNK_DURATION_MS;

    private final SttBlockAudioAssemblyPort audioAssemblyPort;
    private final SttBlockCutDetectionPort cutDetectionPort;
    private final CreateSttBlockPort createSttBlockPort;
    private final CaptureUploadStateRepository captureUploadStateRepository;
    private final SttBlockFormedWriter sttBlockFormedWriter;
    private final CapObjectStoragePort capObjectStoragePort;

    public SttBlockCutTrigger(SttBlockAudioAssemblyPort audioAssemblyPort,
                              SttBlockCutDetectionPort cutDetectionPort,
                              CreateSttBlockPort createSttBlockPort,
                              CaptureUploadStateRepository captureUploadStateRepository,
                              SttBlockFormedWriter sttBlockFormedWriter,
                              CapObjectStoragePort capObjectStoragePort) {
        this.audioAssemblyPort = audioAssemblyPort;
        this.cutDetectionPort = cutDetectionPort;
        this.createSttBlockPort = createSttBlockPort;
        this.captureUploadStateRepository = captureUploadStateRepository;
        this.sttBlockFormedWriter = sttBlockFormedWriter;
        this.capObjectStoragePort = capObjectStoragePort;
    }

    /*
     * 지금 세그먼트에서 마지막 블록 이후로 40개가 쌓였는지(명세 "40개 누적 시") 확인하고,
     * 쌓였으면 블록을 만든다. 임계값 미달이면 아무 일도 하지 않는다 — CaptureUploadService가
     * 매 청크 완료마다 부른다.
     */
    @Async("sttBlockCutTaskExecutor")
    public void triggerIfThresholdReached(Long companyId, Long meetingId) {
        try {
            CaptureUploadState state = captureUploadStateRepository.findByMeetingId(meetingId).orElse(null);
            if (state == null || !hasReachedThreshold(state)) {
                return;
            }

            // 예약 목표 지점은 reservedUpToOffsetMs(선점 기준)에서 이어간다 — lastBlockEndOffsetMs
            // (실제 절단 지점)는 무거운 파이프라인이 끝나야만 갱신되므로, 그걸 기준으로 삼으면
            // 그 사이 뒤이은 트리거가 같은 구간을 또 문턱 통과로 오판한다(레이스, 아래 hasReachedThreshold 참고).
            long targetOffsetMs = state.getReservedUpToOffsetMs() + BLOCK_DURATION_MS;
            // 오디오 조립 시작 지점은 반드시 lastBlockEndOffsetMs(실제 절단 지점)를 써야 한다 —
            // reservedUpToOffsetMs는 예약 북키핑용일 뿐 오디오 경계와는 무관하다.
            long lastBlockEndOffsetMs = state.getLastBlockEndOffsetMs();
            int segmentSeq = state.getSegmentSeq();
            // 지금까지 실제로 올라온 오디오의 끝 지점 — target 지점에 막 도달한 순간이라, 뒤쪽
            // 20초 여유분은 아직 없을 수 있다(CodeRabbit 지적: 트리거를 늦추지 말고 명세대로
            // 40개에서 바로 돈다 — 대신 윈도우 쪽이 이 한도를 넘지 않게 스스로 잘라 쓴다).
            long availableUpToMs = (long) state.getLastSeq() * CHUNK_DURATION_MS;

            // 무거운 작업 전에 이 블록 자리를 먼저 찜한다 — 경합에서 지면 조용히 넘어간다.
            // completesSynchronously=false — 이 트리거는 비동기 파이프라인이라 여기서 안 끝난다.
            // finalizedBlocksCount는 나중에 finalizeBlockOffset이 대신 전진시킨다(그때까지는
            // hasNoPendingReservation()이 false라 다음 블록 예약이 자동으로 막힌다).
            Optional<Integer> reserved = captureUploadStateRepository.tryReserveNextBlockSeq(
                    meetingId, state.getBlocksFormed(), segmentSeq, targetOffsetMs, false);
            if (reserved.isEmpty()) {
                log.info("10분 블록 예약 경합에서 짐 — 다른 트리거가 먼저 처리 중. meetingId={}", meetingId);
                return;
            }
            int blockSeq = reserved.get();

            SttBlockAudioAssemblyPort.ExtractedWindow window =
                    audioAssemblyPort.extractCutWindow(companyId, meetingId, segmentSeq, targetOffsetMs,
                            availableUpToMs);

            // cut-window wav는 절단 지점을 찾는 데만 쓰는 임시본이다 — AI-01 호출이 끝난 이
            // 시점 이후로는 코드 어디서도 다시 참조하지 않으므로 바로 지운다(그동안 아무도
            // 안 지워서 S3에 영구히 쌓이고 있었다). best-effort — 삭제가 실패해도 블록 파이프라인
            // 자체는 계속 진행한다(청소 실패로 STT가 밀리면 안 된다).
            //
            // detectCutPoint를 try/finally로 감싼다(CodeRabbit 지적) — finally 밖에 두면
            // detectCutPoint가 던질 때(AI-01 호출 실패 등) 아래로 못 내려가 방금 만든 cut-window를
            // 영영 못 지운다 — 이 정리를 새로 넣은 이유가 바로 그 orphan을 없애는 거였는데, 실패
            // 경로에서는 여전히 orphan이 남는 반쪽짜리 수정이 된다.
            SttBlockCutDetectionPort.CutDetectionResult cut;
            try {
                cut = cutDetectionPort.detectCutPoint(
                        meetingId, window.s3Key(), window.windowStartOffsetMs(), targetOffsetMs);
            } finally {
                deleteCutWindowBestEffort(window.s3Key());
            }

            String blockAudioS3Key = audioAssemblyPort.assembleBlockAudio(companyId, meetingId,
                    segmentSeq, blockSeq, lastBlockEndOffsetMs, cut.cutOffsetMs());

            createSttBlockPort.createAndSubmitBlock(new CreateSttBlockCommand(
                    meetingId, blockSeq, Math.toIntExact(lastBlockEndOffsetMs),
                    Math.toIntExact(cut.cutOffsetMs()), cut.cutReason(), blockAudioS3Key));

            // blocksFormed는 이미 예약 시점에 전진했다 — 여기서는 끝 지점만 확정한다.
            // segmentSeq를 같이 넘긴다 — 이 파이프라인이 도는 사이 세그먼트가 바뀌었으면
            // (녹음자 이어받기) 새 세그먼트의 리셋된 값을 이 옛 세그먼트 오프셋으로 덮어쓰면
            // 안 되기 때문이다(CodeRabbit 지적).
            sttBlockFormedWriter.finalizeBlockOffset(meetingId, segmentSeq, cut.cutOffsetMs());
        } catch (RuntimeException e) {
            // 클래스 주석대로 던지지 않는다 — 다음 청크가 같은 지점에서 다시 트리거한다.
            log.error("10분 블록 자동 트리거 실패 — meetingId={}", meetingId, e);
        }
    }

    // cut-window wav를 지운다 — 실패해도 로그만 남기고 넘어간다(청소 실패가 STT 파이프라인을
    // 막으면 안 된다). CapObjectStoragePort.deleteRecording은 이름과 달리 범용 단일 키 삭제다
    // (DeleteRecordingService가 잔여 청크를 지울 때도 같은 메서드를 재사용한다).
    private void deleteCutWindowBestEffort(String s3Key) {
        try {
            capObjectStoragePort.deleteRecording(s3Key);
        } catch (RuntimeException e) {
            log.warn("cut-window 임시 오디오 삭제 실패 — s3Key={}", s3Key, e);
        }
    }

    // ⚠️ state.getBlocksFormed()를 여기 쓰면 안 된다 — 세그먼트를 넘어 계속 누적되는 "회의 전체
    // 블록 순번"이라서다. 반면 lastSeq는 세그먼트가 바뀌면 0부터 다시 센다(assignOrVerifyRecorder).
    // 그래서 reservedUpToOffsetMs(세그먼트마다 정확히 0으로 리셋됨)를 청크 개수로 역산해 "이
    // 세그먼트에서 이미 블록으로 예약(선점)된 청크 수"를 구한다.
    //
    // lastBlockEndOffsetMs가 아니라 reservedUpToOffsetMs를 쓴다 — lastBlockEndOffsetMs는 무거운
    // 파이프라인(ffmpeg·AI-01)이 다 끝나야만 갱신되는 "실제 절단 지점"이라, 첫 트리거가 아직
    // 처리 중인 몇 초 사이엔 이 값이 그대로다. 그 창에서 뒤이은(지연된) 트리거가 이 메서드를
    // lastBlockEndOffsetMs 기준으로 판정하면 "아직 문턱 안 넘음"으로 오판해 또 예약 시도를
    // 하게 되고(k6 정합성 테스트로 실제 재현, block_seq 중복 생성 + STT 이중 제출), reservedUpToOffsetMs는
    // blocksFormed CAS와 같은 트랜잭션 안에서 예약 즉시 전진하므로 이 판정에 쓰기에 안전하다.
    private boolean hasReachedThreshold(CaptureUploadState state) {
        // 진행 중인(예약됐지만 아직 안 끝난) 블록이 있으면 여기서 미리 걸러 예약 시도 자체를
        // 안 한다(CodeRabbit 지적, 2차) — 실제 방어는 tryReserveNextBlockSeq의
        // hasNoPendingReservation() 재검증이 하지만, 여기서 먼저 걸러야 뻔히 거절될 예약을
        // DB까지 왕복시키지 않는다.
        if (!state.hasNoPendingReservation()) {
            return false;
        }
        long chunksAlreadyReservedInSegment = state.getReservedUpToOffsetMs() / CHUNK_DURATION_MS;
        long chunksSinceLastBlock = state.getLastSeq() - chunksAlreadyReservedInSegment;
        return chunksSinceLastBlock >= CHUNKS_PER_BLOCK;
    }

    /*
     * 세그먼트 전환 직전(이어받기 성립 시) 이전 세그먼트에 쌓인 자투리를 TAIL 블록으로 마무리한다
     * (CaptureUploadService.issuePartUploadUrls가 state.willChangeSegment()==true일 때, 실제
     * 전환 전에 부른다).
     *
     * <h2>파라미터를 값으로 받는다 — 다시 조회하지 않는다</h2>
     * 이 메서드가 비동기로 실행되는 사이 호출자가 이미 새 세그먼트로 상태를 저장했을 수 있다.
     * 여기서 CaptureUploadState를 다시 읽으면 "바뀌기 전" 값이 아니라 "바뀐 후" 값을 보게 된다 —
     * 그래서 호출 시점의 이전 세그먼트 값을 그대로 인자로 받아 쓴다.
     *
     * <h2>AI-01을 부르지 않는다</h2>
     * TAIL은 무음을 찾아 자르는 게 아니라 "가진 것까지 강제로 끊는" 것이다 — 절단 지점을 결정할
     * 필요가 없으므로 SttBlockCutDetectionPort를 거치지 않는다.
     *
     * <h2>끝 지점을 갱신하지 않는다</h2>
     * 새 세그먼트의 lastBlockEndOffsetMs는 이미 assignOrVerifyRecorder가 세그먼트 전환과 같은
     * 순간(동기)에 0으로 리셋해뒀다 — 이 TAIL 파이프라인이 늦게 도착하거나 실패해도 그 리셋과는
     * 무관하다. 여기서는 예약된 블록을 만들기만 하면 된다.
     */
    @Async("sttBlockCutTaskExecutor")
    public void finalizeTailBlockOnSegmentChange(Long companyId, Long meetingId, int oldSegmentSeq,
                                                 int oldLastSeq, int blocksFormed, long lastBlockEndOffsetMs) {
        // 결과를 보는 호출자가 없다 — 비동기 fire-and-forget이고, 실패해도 뒤이어 지우는 작업이
        // 없어(클래스 주석) 위험하지 않다.
        finalizeTailBlock(companyId, meetingId, oldSegmentSeq, oldLastSeq, blocksFormed, lastBlockEndOffsetMs,
                "세그먼트 전환");
    }

    /*
     * 회의가 끝날 때 마지막 세그먼트에 남은 자투리(40청크 문턱을 못 채운 구간, 최대 10분)를 TAIL
     * 블록으로 마무리한다 — 이게 없으면 그 구간은 STT 블록 자체가 안 생겨서, AnalysisOrchestrator의
     * countUnfinished가 "이미 존재하는 블록 중 안 끝난 것"만 세는 바람에 0으로 나와 분석이 그
     * 구간이 빠진 채로(에러 없이) 돌아간다 — 실제로 겪은 콘텐츠 유실 버그.
     *
     * <h2>일부러 동기(@Async 아님)로 둔다</h2>
     * MeetingCompletedAssemblyTrigger가 이 메서드를 recordingAssemblyDispatcher.dispatch(조립,
     * 완료 후 parts를 지움) 호출 **전에** 부르고 끝날 때까지 기다려야 한다 — 여기서 하는
     * audioAssemblyPort.assembleBlockAudio가 recording_part를 읽어 오디오를 만드는데, 조립이
     * 먼저 그 행과 S3 청크 객체를 지워버리면 TAIL 블록을 만들 재료 자체가 사라진다. 세그먼트
     * 전환 버전(@Async)과 달리 여기는 그런 뒤이은 삭제 작업과 경합할 수 있어 순서를 강제해야 한다.
     *
     * <h2>실패하면 false를 돌려준다(CodeRabbit 지적)</h2>
     * 예약 경합에서 지거나 오디오 조립/제출이 터지면, 호출자는 이 결과를 "괜찮다"로 넘기지 말고
     * 조립 dispatch 자체를 미뤄야 한다 — 여기서 계속 진행해버리면 방금 실패한 TAIL 재료(recording_part)를
     * 조립이 곧 지워버려서 다음 재시도조차 불가능해진다. 자투리가 원래 없던 경우(정상 종료)는
     * true다.
     *
     * <h2>남은 구간이 40청크(BLOCK_DURATION_MS) 이상이면 TAIL로 만들지 않는다(CodeRabbit 지적)</h2>
     * triggerIfThresholdReached는 비동기다 — 회의 종료가 그 트리거보다 먼저 도착하면, 아직
     * lastBlockEndOffsetMs가 갱신 안 된 채로 40청크 이상이 남아 보일 수 있다. 그 전체를 TAIL
     * 하나로 밀어넣으면 원래 AI-01 무음 절단으로 처리됐어야 할 정상 10분 블록 구간까지 절단
     * 탐지 없이 뭉뚱그려 제출하게 된다. 그래서 이 경우 블록을 만들지 않고 FAILED를 돌려준다 —
     * 호출자는 조립을 미루고, 그 사이 진행 중이던 triggerIfThresholdReached가 정상 블록을
     * 마저 확정하면 남은 자투리가 40청크 미만으로 줄어 다음 재시도(CAP-05)에서 정상 처리된다.
     */
    public boolean finalizeTailBlockOnMeetingCompletion(Long companyId, Long meetingId, int lastSegmentSeq,
                                                         int lastSeq, int blocksFormed, long lastBlockEndOffsetMs) {
        return finalizeTailBlock(companyId, meetingId, lastSegmentSeq, lastSeq, blocksFormed, lastBlockEndOffsetMs,
                "회의 종료") != TailFinalizeOutcome.FAILED;
    }

    // NO_LEFTOVER: 마무리할 자투리가 애초에 없었다. FINALIZED: TAIL 블록을 만들어 제출했다.
    // FAILED: 예약 경합에서 졌거나 조립/제출이 터졌다 — finalizeTailBlockOnMeetingCompletion의
    // 호출자는 이 경우 조립을 진행하면 안 된다(위 클래스 주석).
    private enum TailFinalizeOutcome {
        NO_LEFTOVER, FINALIZED, FAILED
    }

    private TailFinalizeOutcome finalizeTailBlock(Long companyId, Long meetingId, int segmentSeq,
                                                   int lastSeqInSegment, int blocksFormed, long lastBlockEndOffsetMs,
                                                   String triggerReason) {
        try {
            long endOffsetMs = (long) lastSeqInSegment * CHUNK_DURATION_MS;
            if (endOffsetMs <= lastBlockEndOffsetMs) {
                // 이미 블록 경계에 딱 맞게 끝났거나, 애초에 이 세그먼트에 청크가 없었다 — 자투리 없음.
                return TailFinalizeOutcome.NO_LEFTOVER;
            }
            if (endOffsetMs - lastBlockEndOffsetMs >= BLOCK_DURATION_MS) {
                // triggerIfThresholdReached(비동기)가 아직 못 따라잡았다 — 이 구간을 TAIL로
                // 뭉개면 안 된다(위 클래스 주석). 그 트리거가 마저 처리할 때까지 실패로 취급한다.
                log.warn("TAIL 마무리 시점에 남은 구간이 이미 한 블록(40청크) 이상이다 — 비동기 10분 "
                                + "트리거가 아직 안 끝났을 수 있다. meetingId={} segmentSeq={} 트리거={}",
                        meetingId, segmentSeq, triggerReason);
                return TailFinalizeOutcome.FAILED;
            }

            // TAIL은 endOffsetMs까지만 선점한다(꽉 찬 BLOCK_DURATION_MS가 아니라 남은 자투리만큼).
            // segmentSeq는 예약 당시(호출자가 넘겨준) 옛 세그먼트 값이다 — 이 시점엔 이미
            // assignOrVerifyRecorder가 세그먼트를 전환해뒀을 수 있어(finalizeTailBlockOnSegmentChange
            // 경로), 지금 실제 세그먼트와 다르면 어댑터가 오프셋을 안 건드리고 blocksFormed만
            // 전진시킨다(위 tryReserveNextBlockSeq 계약 참고).
            // completesSynchronously=true — TAIL은 별도 완료 통보 없이 여기서 조립·제출까지 한
            // 번에 끝난다(끝 지점을 갱신하지 않는다는 클래스 주석과 같은 이유). 예약과 동시에
            // "끝남"까지 표시해야, 이 세그먼트의 마지막 블록 하나 때문에 다음(새 세그먼트)
            // 예약이 영영 막히지 않는다.
            Optional<Integer> reserved = captureUploadStateRepository.tryReserveNextBlockSeq(
                    meetingId, blocksFormed, segmentSeq, endOffsetMs, true);
            if (reserved.isEmpty()) {
                log.warn("TAIL 블록 예약 경합에서 짐 — meetingId={} segmentSeq={} 트리거={}",
                        meetingId, segmentSeq, triggerReason);
                return TailFinalizeOutcome.FAILED;
            }
            int blockSeq = reserved.get();

            String blockAudioS3Key = audioAssemblyPort.assembleBlockAudio(companyId, meetingId, segmentSeq,
                    blockSeq, lastBlockEndOffsetMs, endOffsetMs);

            createSttBlockPort.createAndSubmitBlock(new CreateSttBlockCommand(
                    meetingId, blockSeq, Math.toIntExact(lastBlockEndOffsetMs),
                    Math.toIntExact(endOffsetMs), "TAIL", blockAudioS3Key));
            return TailFinalizeOutcome.FINALIZED;
        } catch (RuntimeException e) {
            // 던지지 않는다 — 세그먼트 전환/회의 종료 자체는 이미 별도로 진행된다(호출자 주석 참고).
            // 대신 FAILED로 알려서, 회의 종료 경로는 조립을 미루게 한다.
            log.error("자투리(TAIL) 블록 마무리 실패 — meetingId={} segmentSeq={} 트리거={}",
                    meetingId, segmentSeq, triggerReason, e);
            return TailFinalizeOutcome.FAILED;
        }
    }
}
