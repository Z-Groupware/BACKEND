package com.module06.backend.cap.application.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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
 * <h2>여기서 던지지 않는다</h2>
 * best-effort다. 예약까지 성공한 뒤 실패해도 청크 업로드 자체(CAP-07)는 이미 성공적으로 끝난
 * 뒤라 되돌릴 것이 없다 — block_seq에 빈 번호 하나가 남을 뿐(해롭지 않다), 다음 트리거가 같은
 * 자리를 다시 경합하는 것보다 안전하다.
 */
@Component
public class SttBlockCutTrigger {

    private static final Logger log = LoggerFactory.getLogger(SttBlockCutTrigger.class);

    // 청크 하나는 15초 — 40개 누적이 곧 10분이다(MAX_SEQ 산정과 같은 물리적 사실).
    private static final long CHUNK_DURATION_MS = 15_000L;
    private static final int CHUNKS_PER_BLOCK = 40;
    private static final long BLOCK_DURATION_MS = CHUNKS_PER_BLOCK * CHUNK_DURATION_MS;

    // ±20초 윈도우가 "미래"를 가리키지 않으려면(CodeRabbit 지적), 절단 지점(target) 뒤쪽 20초
    // 분량도 실제로 업로드돼 있어야 한다 — 그래서 40개가 아니라 42개(≈10분+20초)가 쌓인 뒤에
    // 트리거한다. 블록 자체는 여전히 40개(10분) 지점을 목표로 자른다 — 여유분 2개는 AI-01이
    // "target 뒤쪽에 무음이 있는지" 볼 수 있게 미리 확보해두는 것뿐이다.
    private static final int LOOKAHEAD_CHUNKS_FOR_WINDOW = 2;

    private final SttBlockAudioAssemblyPort audioAssemblyPort;
    private final SttBlockCutDetectionPort cutDetectionPort;
    private final CreateSttBlockPort createSttBlockPort;
    private final CaptureUploadStateRepository captureUploadStateRepository;
    private final SttBlockFormedWriter sttBlockFormedWriter;

    public SttBlockCutTrigger(SttBlockAudioAssemblyPort audioAssemblyPort,
                              SttBlockCutDetectionPort cutDetectionPort,
                              CreateSttBlockPort createSttBlockPort,
                              CaptureUploadStateRepository captureUploadStateRepository,
                              SttBlockFormedWriter sttBlockFormedWriter) {
        this.audioAssemblyPort = audioAssemblyPort;
        this.cutDetectionPort = cutDetectionPort;
        this.createSttBlockPort = createSttBlockPort;
        this.captureUploadStateRepository = captureUploadStateRepository;
        this.sttBlockFormedWriter = sttBlockFormedWriter;
    }

    /*
     * 지금 세그먼트에서 마지막 블록 이후로 40개(+여유 2개)가 쌓였는지 확인하고, 쌓였으면 블록을
     * 만든다. 임계값 미달이면 아무 일도 하지 않는다 — CaptureUploadService가 매 청크 완료마다 부른다.
     */
    @Async("sttBlockCutTaskExecutor")
    public void triggerIfThresholdReached(Long companyId, Long meetingId) {
        try {
            CaptureUploadState state = captureUploadStateRepository.findByMeetingId(meetingId).orElse(null);
            if (state == null || !hasReachedThreshold(state)) {
                return;
            }

            long targetOffsetMs = state.getLastBlockEndOffsetMs() + BLOCK_DURATION_MS;
            long lastBlockEndOffsetMs = state.getLastBlockEndOffsetMs();
            int segmentSeq = state.getSegmentSeq();

            // 무거운 작업 전에 이 블록 자리를 먼저 찜한다 — 경합에서 지면 조용히 넘어간다.
            Optional<Integer> reserved =
                    captureUploadStateRepository.tryReserveNextBlockSeq(meetingId, state.getBlocksFormed());
            if (reserved.isEmpty()) {
                log.info("10분 블록 예약 경합에서 짐 — 다른 트리거가 먼저 처리 중. meetingId={}", meetingId);
                return;
            }
            int blockSeq = reserved.get();

            SttBlockAudioAssemblyPort.ExtractedWindow window =
                    audioAssemblyPort.extractCutWindow(companyId, meetingId, segmentSeq, targetOffsetMs);

            SttBlockCutDetectionPort.CutDetectionResult cut = cutDetectionPort.detectCutPoint(
                    window.s3Key(), window.windowStartOffsetMs(), targetOffsetMs);

            String blockAudioS3Key = audioAssemblyPort.assembleBlockAudio(companyId, meetingId,
                    segmentSeq, blockSeq, lastBlockEndOffsetMs, cut.cutOffsetMs());

            createSttBlockPort.createAndSubmitBlock(new CreateSttBlockCommand(
                    meetingId, blockSeq, Math.toIntExact(lastBlockEndOffsetMs),
                    Math.toIntExact(cut.cutOffsetMs()), cut.cutReason(), blockAudioS3Key));

            // blocksFormed는 이미 예약 시점에 전진했다 — 여기서는 끝 지점만 확정한다.
            sttBlockFormedWriter.finalizeBlockOffset(meetingId, cut.cutOffsetMs());
        } catch (RuntimeException e) {
            // 클래스 주석대로 던지지 않는다 — 다음 청크가 같은 지점에서 다시 트리거한다.
            log.error("10분 블록 자동 트리거 실패 — meetingId={}", meetingId, e);
        }
    }

    // ⚠️ state.getBlocksFormed()를 여기 쓰면 안 된다 — 세그먼트를 넘어 계속 누적되는 "회의 전체
    // 블록 순번"이라서다. 반면 lastSeq는 세그먼트가 바뀌면 0부터 다시 센다(assignOrVerifyRecorder).
    // 그래서 lastBlockEndOffsetMs(세그먼트마다 정확히 0으로 리셋됨)를 청크 개수로 역산해 "이
    // 세그먼트에서 이미 블록으로 소비된 청크 수"를 구한다.
    private boolean hasReachedThreshold(CaptureUploadState state) {
        long chunksAlreadyConsumedInSegment = state.getLastBlockEndOffsetMs() / CHUNK_DURATION_MS;
        long chunksSinceLastBlock = state.getLastSeq() - chunksAlreadyConsumedInSegment;
        return chunksSinceLastBlock >= CHUNKS_PER_BLOCK + LOOKAHEAD_CHUNKS_FOR_WINDOW;
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
        try {
            long endOffsetMs = (long) oldLastSeq * CHUNK_DURATION_MS;
            if (endOffsetMs <= lastBlockEndOffsetMs) {
                // 이미 블록 경계에 딱 맞게 끝났거나, 애초에 이 세그먼트에 청크가 없었다 — 자투리 없음.
                return;
            }

            Optional<Integer> reserved = captureUploadStateRepository.tryReserveNextBlockSeq(meetingId, blocksFormed);
            if (reserved.isEmpty()) {
                log.info("TAIL 블록 예약 경합에서 짐 — meetingId={} oldSegmentSeq={}", meetingId, oldSegmentSeq);
                return;
            }
            int blockSeq = reserved.get();

            String blockAudioS3Key = audioAssemblyPort.assembleBlockAudio(companyId, meetingId, oldSegmentSeq,
                    blockSeq, lastBlockEndOffsetMs, endOffsetMs);

            createSttBlockPort.createAndSubmitBlock(new CreateSttBlockCommand(
                    meetingId, blockSeq, Math.toIntExact(lastBlockEndOffsetMs),
                    Math.toIntExact(endOffsetMs), "TAIL", blockAudioS3Key));
        } catch (RuntimeException e) {
            // 여기서도 던지지 않는다 — 세그먼트 전환(이어받기) 자체는 이미 별도로 진행된다.
            log.error("세그먼트 전환 시 자투리(TAIL) 블록 마무리 실패 — meetingId={} oldSegmentSeq={}",
                    meetingId, oldSegmentSeq, e);
        }
    }
}
