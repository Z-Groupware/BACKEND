package com.module06.backend.cap.application.service;

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
 * <h2>세그먼트를 넘어가지 않는다(단순화 결정)</h2>
 * 녹음자 이어받기로 세그먼트가 바뀌면, 그 시점까지 쌓인 청크만으로 블록을 마무리하지 않고
 * *이번 트리거에서는* 새 세그먼트에서 카운트를 처음부터 다시 센다 — 세그먼트 경계를 넘어
 * 오디오를 이어붙이는 연속성 처리는 범위 밖으로 남긴다(추후 별도 작업).
 *
 * <h2>여기서 던지지 않는다</h2>
 * best-effort다. 실패해도 청크 업로드 자체(CAP-07)는 이미 성공적으로 끝난 뒤라 되돌릴 것이
 * 없고, blocksFormed가 그대로면 다음 청크 완료 시 같은 지점에서 다시 트리거된다.
 */
@Component
public class SttBlockCutTrigger {

    private static final Logger log = LoggerFactory.getLogger(SttBlockCutTrigger.class);

    // 청크 하나는 15초 — 40개 누적이 곧 10분이다(MAX_SEQ 산정과 같은 물리적 사실).
    private static final int CHUNKS_PER_BLOCK = 40;
    private static final long BLOCK_DURATION_MS = 600_000L;

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
     * 지금 세그먼트에서 마지막 블록 이후로 40개가 쌓였는지 확인하고, 쌓였으면 블록을 만든다.
     * 임계값 미달이면 아무 일도 하지 않는다 — CaptureUploadService가 매 청크 완료마다 부른다.
     */
    @Async("sttBlockCutTaskExecutor")
    public void triggerIfThresholdReached(Long companyId, Long meetingId) {
        try {
            CaptureUploadState state = captureUploadStateRepository.findByMeetingId(meetingId).orElse(null);
            if (state == null || !hasReachedThreshold(state)) {
                return;
            }

            long targetOffsetMs = state.getLastBlockEndOffsetMs() + BLOCK_DURATION_MS;
            int blockSeq = state.getBlocksFormed();

            SttBlockAudioAssemblyPort.ExtractedWindow window =
                    audioAssemblyPort.extractCutWindow(companyId, meetingId, state.getSegmentSeq(), targetOffsetMs);

            SttBlockCutDetectionPort.CutDetectionResult cut = cutDetectionPort.detectCutPoint(
                    window.s3Key(), window.windowStartOffsetMs(), targetOffsetMs);

            String blockAudioS3Key = audioAssemblyPort.assembleBlockAudio(companyId, meetingId,
                    state.getSegmentSeq(), blockSeq, state.getLastBlockEndOffsetMs(), cut.cutOffsetMs());

            createSttBlockPort.createAndSubmitBlock(new CreateSttBlockCommand(
                    meetingId, blockSeq, Math.toIntExact(state.getLastBlockEndOffsetMs()),
                    Math.toIntExact(cut.cutOffsetMs()), cut.cutReason(), blockAudioS3Key));

            // 블록 생성·제출까지 끝난 뒤에만 카운터를 전진시킨다 — 실패했으면 다음 청크가 같은
            // 지점에서 다시 시도해야 한다.
            sttBlockFormedWriter.recordBlockFormed(meetingId, cut.cutOffsetMs());
        } catch (RuntimeException e) {
            // 클래스 주석대로 던지지 않는다 — 다음 청크가 같은 지점에서 다시 트리거한다.
            log.error("10분 블록 자동 트리거 실패 — meetingId={}", meetingId, e);
        }
    }

    private boolean hasReachedThreshold(CaptureUploadState state) {
        int chunksSinceLastBlock = state.getLastSeq() - state.getBlocksFormed() * CHUNKS_PER_BLOCK;
        return chunksSinceLastBlock >= CHUNKS_PER_BLOCK;
    }
}
