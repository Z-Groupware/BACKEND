package com.module06.backend.cap.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.port.out.RecordingAssemblyPort;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.meeting.application.event.MeetingCompletionRequestedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/*
 * MEET-08 회의 종료 → 자동 녹음 조립(CAP-05의 자동 경로).
 *
 * 검증의 축은 부르는가 · 안 부르는가 둘이다 — 잘못 부르면 아직 안 올라온 청크로 조립을 시도하고,
 * 잘못 안 부르면 사람이 CAP-05를 직접 눌러야만 녹음이 남는다(대부분 그럴 필요 없다는 게 명세).
 *
 * 실행 흐름(AFTER_COMMIT · 비동기)은 여기서 검증하지 않는다 — 애너테이션이 붙었는지는 컨텍스트를
 * 띄우는 테스트가 볼 일이다.
 */
class MeetingCompletedAssemblyTriggerTest {

    private static final long MEETING = 500L;
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 8, 10, 15, 2, 40);

    @Test
    @DisplayName("이미 등록된 녹음본이 있으면 조립을 부르지 않는다")
    void 이미_녹음본이_있으면_부르지_않는다() {
        RecordingAssemblyCalls port = new RecordingAssemblyCalls();

        trigger(true, state(0, 3), Map.of(0, List.of(1, 2, 3)), port).onMeetingCompleted(event());

        assertThat(port.calls).isEmpty();
    }

    @Test
    @DisplayName("녹음이 시작된 적 없는 회의는 조립을 부르지 않는다")
    void 녹음_상태가_없으면_부르지_않는다() {
        RecordingAssemblyCalls port = new RecordingAssemblyCalls();

        trigger(false, Optional.empty(), Map.of(), port).onMeetingCompleted(event());

        assertThat(port.calls).isEmpty();
    }

    @Test
    @DisplayName("업로드된 청크가 하나도 없으면 조립을 부르지 않는다")
    void 업로드된_청크가_없으면_부르지_않는다() {
        RecordingAssemblyCalls port = new RecordingAssemblyCalls();

        trigger(false, state(0, 0), Map.of(), port).onMeetingCompleted(event());

        assertThat(port.calls).isEmpty();
    }

    @Test
    @DisplayName("중간 순번이 빠졌으면 조립을 부르지 않는다")
    void 구멍이_있으면_부르지_않는다() {
        RecordingAssemblyCalls port = new RecordingAssemblyCalls();

        trigger(false, state(0, 4), Map.of(0, List.of(1, 2, 4)), port).onMeetingCompleted(event());

        assertThat(port.calls).isEmpty();
    }

    @Test
    @DisplayName("연속이면 우리 DB의 segmentSeq·lastSeq 그대로 조립을 부른다")
    void 연속이면_조립을_부른다() {
        RecordingAssemblyCalls port = new RecordingAssemblyCalls();

        trigger(false, state(0, 3), Map.of(0, List.of(1, 2, 3)), port).onMeetingCompleted(event());

        assertThat(port.calls).hasSize(1);
        assertThat(port.calls.get(0).meetingId()).isEqualTo(MEETING);
        assertThat(port.calls.get(0).lastSegmentSeq()).isEqualTo(0);
        assertThat(port.calls.get(0).lastSeq()).isEqualTo(3);
    }

    @Test
    @DisplayName("녹음자 이어받기로 세그먼트가 여러 개여도 마지막 세그먼트 기준으로 조립을 부른다")
    void 여러_세그먼트여도_조립을_부른다() {
        RecordingAssemblyCalls port = new RecordingAssemblyCalls();

        trigger(false, state(1, 2), Map.of(0, List.of(1, 2, 3), 1, List.of(1, 2)), port).onMeetingCompleted(event());

        assertThat(port.calls).hasSize(1);
        assertThat(port.calls.get(0).lastSegmentSeq()).isEqualTo(1);
        assertThat(port.calls.get(0).lastSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("조립 트리거가 터져도 밖으로 예외를 내지 않는다 — 회의 종료를 되돌리지 않는다")
    void 조립_실패가_밖으로_나가지_않는다() {
        RecordingAssemblyPort exploding = (meetingId, lastSegmentSeq, lastSeq) -> {
            throw new IllegalStateException("조립 파이프라인 호출이 터졌다");
        };
        MeetingCompletedAssemblyTrigger trigger = new MeetingCompletedAssemblyTrigger(
                recordingRepo(false), stateRepo(state(0, 3)), gapChecker(Map.of(0, List.of(1, 2, 3))), exploding);

        assertThatCode(() -> trigger.onMeetingCompleted(event())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("녹음본 조회 자체가 터져도 밖으로 예외를 내지 않는다")
    void 조회_실패도_밖으로_나가지_않는다() {
        RecordingRepository exploding = new RecordingRepository() {
            @Override
            public Recording save(Recording recording) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public boolean existsByMeetingId(Long meetingId) {
                throw new IllegalStateException("커넥션 풀이 말랐다");
            }

            @Override
            public Optional<Recording> findByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        RecordingAssemblyCalls port = new RecordingAssemblyCalls();
        MeetingCompletedAssemblyTrigger trigger = new MeetingCompletedAssemblyTrigger(
                exploding, stateRepo(state(0, 3)), gapChecker(Map.of(0, List.of(1, 2, 3))), port);

        assertThatCode(() -> trigger.onMeetingCompleted(event())).doesNotThrowAnyException();
        assertThat(port.calls).isEmpty();
    }

    // ── 조립 헬퍼 ──────────────────────────────────────────────────────────

    private MeetingCompletedAssemblyTrigger trigger(boolean recordingExists, Optional<CaptureUploadState> state,
                                                     Map<Integer, List<Integer>> seqsBySegment,
                                                     RecordingAssemblyPort port) {
        return new MeetingCompletedAssemblyTrigger(
                recordingRepo(recordingExists), stateRepo(state), gapChecker(seqsBySegment), port);
    }

    private RecordingRepository recordingRepo(boolean exists) {
        return new RecordingRepository() {
            @Override
            public Recording save(Recording recording) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public boolean existsByMeetingId(Long meetingId) {
                return exists;
            }

            @Override
            public Optional<Recording> findByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
    }

    private CaptureUploadStateRepository stateRepo(Optional<CaptureUploadState> state) {
        return new CaptureUploadStateRepository() {
            @Override
            public Optional<CaptureUploadState> findByMeetingId(Long meetingId) {
                return state;
            }

            @Override
            public CaptureUploadState save(CaptureUploadState value) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
    }

    private RecordingGapChecker gapChecker(Map<Integer, List<Integer>> seqsBySegment) {
        RecordingPartRepository partRepository = new RecordingPartRepository() {
            @Override
            public RecordingPart save(RecordingPart recordingPart) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq) {
                return seqsBySegment.getOrDefault(segmentSeq, List.of());
            }

            @Override
            public List<RecordingPart> findInSegmentBetweenSeqs(Long meetingId, int segmentSeq, int fromSeq,
                                                                 int toSeq) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        return new RecordingGapChecker(partRepository);
    }

    private Optional<CaptureUploadState> state(int segmentSeq, int lastSeq) {
        return Optional.of(CaptureUploadState.restore(MEETING, segmentSeq, 7L, lastSeq, 0, 0L, null, null));
    }

    private MeetingCompletionRequestedEvent event() {
        return new MeetingCompletionRequestedEvent(7L, MEETING, 900L, COMPLETED_AT);
    }

    private static final class RecordingAssemblyCalls implements RecordingAssemblyPort {

        private final List<Call> calls = new ArrayList<>();

        @Override
        public void startAssembly(Long meetingId, int lastSegmentSeq, int lastSeq) {
            calls.add(new Call(meetingId, lastSegmentSeq, lastSeq));
        }

        private record Call(Long meetingId, int lastSegmentSeq, int lastSeq) {
        }
    }
}
