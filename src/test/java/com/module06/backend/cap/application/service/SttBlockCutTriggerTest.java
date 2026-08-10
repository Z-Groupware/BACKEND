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
 * 10분/40청크 자동 블록 트리거(SttBlockCutTrigger)의 임계값 판정·예약(CAS) 순서·파이프라인 순서·
 * 실패 시 best-effort(카운터 미갱신) 규칙을 검증하는 단위 테스트다. 트리거는 명세대로 정확히
 * 40개에서 돈다(CodeRabbit 지적 — 여유분을 채우려고 트리거 자체를 늦추면 안 된다) — 대신 아직
 * 안 올라온 뒤쪽 20초를 요청하지 않도록 윈도우 쪽에서 스스로 잘라 쓴다(availableUpToMs).
 *
 * @Async 어노테이션은 순수 호출(new)로 조립한 이 테스트에선 적용되지 않으므로, 메서드가
 * 동기적으로 바로 실행된다.
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
    @DisplayName("40개 누적이면(여유분 없어도) 예약→윈도우 추출→절단 탐지→블록 조립→블록 생성 순서로 돈다")
    void runsFullPipelineExactlyAtFortyChunks() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        state.recordUpload(7L, 40);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        RecordingAudioAssemblyPort audioPort = new RecordingAudioAssemblyPort();
        RecordingCutDetectionPort cutPort = new RecordingCutDetectionPort();
        RecordingCreatePort createPort = new RecordingCreatePort();

        trigger(stateRepo, audioPort, cutPort, createPort).triggerIfThresholdReached(COMPANY_ID, MEETING_ID);

        // 목표 지점(targetOffsetMs)은 15초×40청크 = 600,000ms.
        assertThat(audioPort.extractCalls).containsExactly(600_000L);
        // 딱 40개만 올라온 시점이라 availableUpToMs도 600,000ms — 뒤쪽 20초 여유분은 아직 없다.
        assertThat(audioPort.availableUpToCalls).containsExactly(600_000L);
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
    @DisplayName("뒤쪽 여유분 청크가 이미 올라와 있으면 availableUpToMs가 그만큼 더 크게 전달된다")
    void passesLargerAvailableUpToMsWhenLookaheadChunksExist() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        // 42개(=target 뒤 20초 분량 포함)가 이미 올라온 상황 — 트리거는 여전히 40개 지점에서
        // 이미 발화 조건을 만족하지만, availableUpToMs는 실제 업로드분(42개=630,000ms)을 반영한다.
        state.recordUpload(7L, 42);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        RecordingAudioAssemblyPort audioPort = new RecordingAudioAssemblyPort();
        RecordingCutDetectionPort cutPort = new RecordingCutDetectionPort();
        RecordingCreatePort createPort = new RecordingCreatePort();

        trigger(stateRepo, audioPort, cutPort, createPort).triggerIfThresholdReached(COMPANY_ID, MEETING_ID);

        assertThat(audioPort.extractCalls).containsExactly(600_000L);
        assertThat(audioPort.availableUpToCalls).containsExactly(630_000L);
    }

    @Test
    @DisplayName("예약에서 경합에 지면(blocksFormed가 기대와 다르면) 아무 포트도 안 부르고 조용히 넘어간다")
    void skipsWhenReservationLosesRace() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        state.recordUpload(7L, 40);
        // 이미 누가 먼저 예약해간 상황을 흉내낸다 — FakeStateRepo가 기대값 불일치로 항상 empty를 준다.
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        stateRepo.forceReservationConflict = true;
        RecordingCreatePort createPort = new RecordingCreatePort();

        trigger(stateRepo, new RefusingAudioAssemblyPort(), new RefusingCutDetectionPort(), createPort)
                .triggerIfThresholdReached(COMPANY_ID, MEETING_ID);

        assertThat(createPort.received).isEmpty();
    }

    @Test
    @DisplayName("파이프라인 도중 실패하면 예외를 던지지 않는다(카운터는 예약 시점에 이미 전진해 있다)")
    void swallowsFailureAfterReservation() {
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        state.recordUpload(7L, 40);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        SttBlockAudioAssemblyPort failingAudioPort = new SttBlockAudioAssemblyPort() {
            @Override
            public ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq,
                                                     long targetOffsetMs, long availableUpToMs) {
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
        // 예약은 무거운 작업 전에 이미 성공했으므로 blocksFormed는 전진해 있다(block_seq 하나가
        // 빈 채로 남을 뿐 — 해롭지 않다는 게 클래스 설계 의도).
        assertThat(state.getBlocksFormed()).isEqualTo(1);
    }

    @Test
    @DisplayName("이어받기로 세그먼트가 바뀐 뒤에도(blocksFormed는 유지, lastSeq는 리셋) 40개면 트리거된다")
    void triggersInNewSegmentRegardlessOfPriorBlocksFormed() {
        // 이전 세그먼트에서 블록 2개를 이미 만든 뒤(blocksFormed=2) 이어받기가 일어난 상황을
        // 재현한다 — assignOrVerifyRecorder가 lastSeq·lastBlockEndOffsetMs를 0으로 리셋한다.
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);
        state.reserveNextBlockSeq();
        state.finalizeBlockOffsetIfSegmentMatches(0, 600_000L);
        state.reserveNextBlockSeq();
        state.finalizeBlockOffsetIfSegmentMatches(0, 1_200_000L);
        state.assignOrVerifyRecorder(9L, true);
        assertThat(state.getBlocksFormed()).isEqualTo(2);
        assertThat(state.getLastBlockEndOffsetMs()).isZero();
        assertThat(state.getLastSeq()).isZero();

        // 새 세그먼트에서 정확히 40개만 올라왔다 — blocksFormed(2)와 무관하게 트리거돼야 한다.
        state.recordUpload(9L, 40);
        FakeStateRepo stateRepo = new FakeStateRepo(state);
        RecordingAudioAssemblyPort audioPort = new RecordingAudioAssemblyPort();
        RecordingCutDetectionPort cutPort = new RecordingCutDetectionPort();
        RecordingCreatePort createPort = new RecordingCreatePort();

        trigger(stateRepo, audioPort, cutPort, createPort).triggerIfThresholdReached(COMPANY_ID, MEETING_ID);

        assertThat(audioPort.extractCalls).containsExactly(600_000L);
        assertThat(createPort.received).hasSize(1);
        assertThat(createPort.received.get(0).blockSeq()).isEqualTo(2);
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
    @DisplayName("세그먼트 전환 시 자투리가 있으면 AI-01 없이 바로 예약하고 블록을 조립·생성한다")
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

        // 예약을 통해 blocksFormed만 전진한다 — 새 세그먼트의 끝 지점 리셋은 이 메서드의 몫이 아니다
        // (assignOrVerifyRecorder가 세그먼트 전환과 같은 순간에 이미 처리했다는 전제).
        assertThat(state.getBlocksFormed()).isEqualTo(1);
    }

    private SttBlockCutTrigger trigger(CaptureUploadStateRepository stateRepo, SttBlockAudioAssemblyPort audioPort,
                                       SttBlockCutDetectionPort cutPort, CreateSttBlockPort createPort) {
        return new SttBlockCutTrigger(audioPort, cutPort, createPort, stateRepo, new SttBlockFormedWriter(stateRepo));
    }

    // 실제 CAS 동작을 흉내낸다 — expectedBlocksFormed가 현재 state.blocksFormed와 같을 때만
    // 예약(도메인의 reserveNextBlockSeq)이 성립한다. forceReservationConflict=true면 항상 실패한다
    // (다른 트리거가 먼저 예약해간 경합 상황 재현용).
    private static final class FakeStateRepo implements CaptureUploadStateRepository {
        private CaptureUploadState state;
        private boolean forceReservationConflict = false;

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

        @Override
        public Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed) {
            if (forceReservationConflict || state.getBlocksFormed() != expectedBlocksFormed) {
                return Optional.empty();
            }
            return Optional.of(state.reserveNextBlockSeq());
        }
    }

    private static final class RefusingAudioAssemblyPort implements SttBlockAudioAssemblyPort {
        @Override
        public ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq,
                                                 long targetOffsetMs, long availableUpToMs) {
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
        public CutDetectionResult detectCutPoint(long meetingId, String windowAudioS3Key, long windowStartOffsetMs,
                                                 long targetOffsetMs) {
            throw new AssertionError("임계값 미달이거나 앞 단계가 실패했으므로 호출되면 안 됩니다.");
        }
    }

    private static final class RecordingAudioAssemblyPort implements SttBlockAudioAssemblyPort {
        private final List<Long> extractCalls = new ArrayList<>();
        private final List<Long> availableUpToCalls = new ArrayList<>();
        private final List<Object> assembleCalls = new ArrayList<>();

        @Override
        public ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq,
                                                 long targetOffsetMs, long availableUpToMs) {
            extractCalls.add(targetOffsetMs);
            availableUpToCalls.add(availableUpToMs);
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
        public CutDetectionResult detectCutPoint(long meetingId, String windowAudioS3Key, long windowStartOffsetMs,
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
