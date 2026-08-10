package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.port.out.SttBlockAudioAssemblyPort;
import com.module06.backend.cap.application.port.out.SttBlockCutDetectionPort;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.capture.application.port.in.CreateSttBlockPort;
import com.module06.backend.capture.application.port.in.CreateSttBlockPort.CreateSttBlockCommand;

/*
 * 10분/40청크 자동 블록 트리거(SttBlockCutTrigger)의 임계값 판정·파이프라인 순서·실패 시
 * best-effort(상태 미갱신) 규칙을 검증하는 단위 테스트다. @Async 어노테이션은 순수 호출(new)로
 * 조립한 이 테스트에선 적용되지 않으므로, 메서드가 동기적으로 바로 실행된다.
 */
@DisplayName("10분/40청크 자동 블록 트리거")
class SttBlockCutTriggerTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long MEETING_ID = 500L;

    @Test
    @DisplayName("40개 미만이면 아무 포트도 부르지 않는다")
    void doesNothingBelowThreshold() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        state.recordUpload(7L, 39);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        RefusingAudioAssemblyPort audioPort = new RefusingAudioAssemblyPort();
        RefusingCutDetectionPort cutPort = new RefusingCutDetectionPort();
        RecordingCreatePort createPort = new RecordingCreatePort();

        trigger(stateRepo, audioPort, cutPort, createPort).triggerIfThresholdReached(COMPANY_ID, MEETING_ID);

        assertThat(createPort.received).isEmpty();
        assertThat(state.getBlocksFormed()).isZero();
    }

    @Test
    @DisplayName("40개 누적이면 윈도우 추출→절단 탐지→블록 조립→블록 생성 순서로 파이프라인 전체를 돈다")
    void runsFullPipelineAtThreshold() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        state.recordUpload(7L, 40);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        RecordingAudioAssemblyPort audioPort = new RecordingAudioAssemblyPort();
        RecordingCutDetectionPort cutPort = new RecordingCutDetectionPort();
        RecordingCreatePort createPort = new RecordingCreatePort();

        trigger(stateRepo, audioPort, cutPort, createPort).triggerIfThresholdReached(COMPANY_ID, MEETING_ID);

        // 목표 지점(targetOffsetMs)은 15초×40청크 = 600,000ms 근사치다.
        assertThat(audioPort.extractCalls).containsExactly(600_000L);
        assertThat(cutPort.detectCalls).containsExactly(600_000L);
        assertThat(audioPort.assembleCalls).hasSize(1);
        assertThat(createPort.received).hasSize(1);

        CreateSttBlockCommand created = createPort.received.get(0);
        assertThat(created.meetingId()).isEqualTo(MEETING_ID);
        assertThat(created.blockSeq()).isZero();
        assertThat(created.startOffsetMs()).isZero();
        assertThat(created.endOffsetMs()).isEqualTo(600_000);
        assertThat(created.cutReason()).isEqualTo("VAD_SILENCE");

        // 파이프라인이 끝까지 성공했으므로 카운터가 전진해야 한다.
        assertThat(state.getBlocksFormed()).isEqualTo(1);
        assertThat(state.getLastBlockEndOffsetMs()).isEqualTo(600_000L);
    }

    @Test
    @DisplayName("파이프라인 도중 실패하면 예외를 던지지 않고, 블록 카운터도 갱신하지 않는다")
    void swallowsFailureAndLeavesCounterUnchanged() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        state.recordUpload(7L, 40);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        SttBlockAudioAssemblyPort failingAudioPort = new SttBlockAudioAssemblyPort() {
            @Override
            public ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq,
                                                     long targetOffsetMs) {
                throw new RuntimeException("S3/ffmpeg 실패 가정");
            }

            @Override
            public String assembleBlockAudio(Long companyId, Long meetingId, int segmentSeq, int blockSeq,
                                             long startOffsetMs, long endOffsetMs) {
                throw new AssertionError("윈도우 추출이 실패했으므로 호출되면 안 됩니다.");
            }
        };
        RecordingCreatePort createPort = new RecordingCreatePort();

        SttBlockCutTrigger trigger = trigger(stateRepo, failingAudioPort, new RefusingCutDetectionPort(), createPort);

        // 예외가 바깥으로 안 새어나가야 한다(best-effort).
        trigger.triggerIfThresholdReached(COMPANY_ID, MEETING_ID);

        assertThat(createPort.received).isEmpty();
        assertThat(state.getBlocksFormed()).isZero();
    }

    @Test
    @DisplayName("TAIL로 blocksFormed가 이미 늘어난 새 세그먼트에서도 40개만 쌓이면 트리거된다")
    void triggersAtFortyChunksEvenAfterBlocksFormedAdvancedByTailInPriorSegment() {
        // 이전 세그먼트에서 TAIL 블록까지 포함해 blocksFormed=3이 된 뒤, 새 세그먼트가 시작된 상황을
        // 재현한다(startNewSegmentBlockCounting 직후와 동일 — lastBlockEndOffsetMs는 0으로 리셋).
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        state.recordBlockFormed(600_000L);
        state.recordBlockFormed(1_200_000L);
        state.startNewSegmentBlockCounting();
        assertThat(state.getBlocksFormed()).isEqualTo(3);
        assertThat(state.getLastBlockEndOffsetMs()).isZero();

        // 새 세그먼트에서 정확히 40개(=10분)만 올라왔다 — blocksFormed(3)와 무관하게 트리거돼야 한다.
        state.recordUpload(7L, 40);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        RecordingAudioAssemblyPort audioPort = new RecordingAudioAssemblyPort();
        RecordingCutDetectionPort cutPort = new RecordingCutDetectionPort();
        RecordingCreatePort createPort = new RecordingCreatePort();

        trigger(stateRepo, audioPort, cutPort, createPort).triggerIfThresholdReached(COMPANY_ID, MEETING_ID);

        assertThat(audioPort.extractCalls).containsExactly(600_000L);
        assertThat(createPort.received).hasSize(1);
        assertThat(createPort.received.get(0).blockSeq()).isEqualTo(3);
    }

    @Test
    @DisplayName("세그먼트 전환 시 자투리가 없으면(청크 없음/이미 경계에 맞음) 아무 포트도 부르지 않는다")
    void skipsTailFinalizationWhenNothingAccumulated() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        RecordingCreatePort createPort = new RecordingCreatePort();

        trigger(stateRepo, new RefusingAudioAssemblyPort(), new RefusingCutDetectionPort(), createPort)
                .finalizeTailBlockOnSegmentChange(COMPANY_ID, MEETING_ID, 0, 0, 0, 0L);

        assertThat(createPort.received).isEmpty();
        assertThat(state.getBlocksFormed()).isZero();
    }

    @Test
    @DisplayName("세그먼트 전환 시 자투리가 있으면 AI-01 없이 바로 블록을 조립·생성하고 카운터를 갱신한다")
    void finalizesTailBlockWhenChunksAccumulated() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        RecordingAudioAssemblyPort audioPort = new RecordingAudioAssemblyPort();
        RecordingCreatePort createPort = new RecordingCreatePort();

        // 세그먼트 0에서 25개(=375,000ms)만 쌓인 채 이어받기가 일어난 상황.
        trigger(stateRepo, audioPort, new RefusingCutDetectionPort(), createPort)
                .finalizeTailBlockOnSegmentChange(COMPANY_ID, MEETING_ID, 0, 25, 0, 0L);

        assertThat(audioPort.assembleCalls).hasSize(1);
        assertThat(createPort.received).hasSize(1);

        CreateSttBlockCommand created = createPort.received.get(0);
        assertThat(created.meetingId()).isEqualTo(MEETING_ID);
        assertThat(created.blockSeq()).isZero();
        assertThat(created.startOffsetMs()).isZero();
        assertThat(created.endOffsetMs()).isEqualTo(375_000);
        assertThat(created.cutReason()).isEqualTo("TAIL");

        // 세그먼트 전환용 카운터 갱신 — blocksFormed는 오르고 끝 지점은 새 세그먼트를 위해 0으로 리셋.
        assertThat(state.getBlocksFormed()).isEqualTo(1);
        assertThat(state.getLastBlockEndOffsetMs()).isZero();
    }

    private SttBlockCutTrigger trigger(CaptureUploadStateRepository stateRepo, SttBlockAudioAssemblyPort audioPort,
                                       SttBlockCutDetectionPort cutPort, CreateSttBlockPort createPort) {
        return new SttBlockCutTrigger(audioPort, cutPort, createPort, stateRepo, new SttBlockFormedWriter(stateRepo));
    }

    private static final class FakeStateRepo implements CaptureUploadStateRepository {
        private CaptureUploadState state;

        private FakeStateRepo(CaptureUploadState state) {
            this.state = state;
        }

        @Override
        public Optional<CaptureUploadState> findByMeetingId(Long meetingId) {
            return Optional.ofNullable(state);
        }

        @Override
        public CaptureUploadState save(CaptureUploadState toSave) {
            this.state = toSave;
            return toSave;
        }
    }

    private static final class RefusingAudioAssemblyPort implements SttBlockAudioAssemblyPort {
        @Override
        public ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq,
                                                 long targetOffsetMs) {
            throw new AssertionError("임계값 미달이므로 호출되면 안 됩니다.");
        }

        @Override
        public String assembleBlockAudio(Long companyId, Long meetingId, int segmentSeq, int blockSeq,
                                         long startOffsetMs, long endOffsetMs) {
            throw new AssertionError("임계값 미달이므로 호출되면 안 됩니다.");
        }
    }

    private static final class RefusingCutDetectionPort implements SttBlockCutDetectionPort {
        @Override
        public CutDetectionResult detectCutPoint(String windowAudioS3Key, long windowStartOffsetMs,
                                                 long targetOffsetMs) {
            throw new AssertionError("임계값 미달이거나 앞 단계가 실패했으므로 호출되면 안 됩니다.");
        }
    }

    private static final class RecordingAudioAssemblyPort implements SttBlockAudioAssemblyPort {
        private final List<Long> extractCalls = new ArrayList<>();
        private final List<Object> assembleCalls = new ArrayList<>();

        @Override
        public ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq,
                                                 long targetOffsetMs) {
            extractCalls.add(targetOffsetMs);
            return new ExtractedWindow("stt-temp/org-1/meeting-500/cut-window.wav", targetOffsetMs - 20_000L);
        }

        @Override
        public String assembleBlockAudio(Long companyId, Long meetingId, int segmentSeq, int blockSeq,
                                         long startOffsetMs, long endOffsetMs) {
            assembleCalls.add(new Object());
            return "stt-temp/org-1/meeting-500/blocks/" + blockSeq + ".wav";
        }
    }

    private static final class RecordingCutDetectionPort implements SttBlockCutDetectionPort {
        private final List<Long> detectCalls = new ArrayList<>();

        @Override
        public CutDetectionResult detectCutPoint(String windowAudioS3Key, long windowStartOffsetMs,
                                                 long targetOffsetMs) {
            detectCalls.add(targetOffsetMs);
            return new CutDetectionResult(targetOffsetMs, "VAD_SILENCE", 700L);
        }
    }

    private static final class RecordingCreatePort implements CreateSttBlockPort {
        private final List<CreateSttBlockCommand> received = new ArrayList<>();

        @Override
        public void createAndSubmitBlock(CreateSttBlockCommand command) {
            received.add(command);
        }
    }
}
