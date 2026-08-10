package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.application.port.out.SttBlockRepository.PendingBlock;
import com.module06.backend.capture.application.port.out.SttJobResultPort;
import com.module06.backend.capture.application.port.out.SttJobResultPort.SttJobOutcome;
import com.module06.backend.capture.application.port.out.SttJobResultPort.State;
import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.port.out.TranscriptRepository.NewUtterance;
import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.domain.model.TranscriptSegmenter.Word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STT 결과 폴링.
 *
 * <p>검증의 축이 둘이다. <b>적재가 DONE 보다 먼저인가</b> — 순서가 뒤집히면 분석 시작 관문이
 * 통과되고 전사가 빈 회의가 분석된다. 그리고 <b>못 읽은 것을 실패로 접지 않는가</b> —
 * 네트워크가 흔들린 것을 FAILED 로 닫으면 사람이 재처리를 눌러 요금이 두 번 나간다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SttResultPollingServiceTest {

    private static final long BLOCK_ID = 11L;
    private static final long MEETING = 500L;
    private static final int BLOCK_SEQ = 3;
    /* 이 블록은 회의 시작 후 29분 54초 지점부터다. 제공자 오프셋에 이 값이 더해져야 한다. */
    private static final int BLOCK_START_MS = 1_794_000;

    @Mock
    private SttBlockRepository sttBlockRepository;

    @Mock
    private SttJobResultPort sttJobResultPort;

    @Mock
    private TranscriptRepository transcriptRepository;

    private SttResultPollingService service() {
        return new SttResultPollingService(sttBlockRepository, sttJobResultPort, transcriptRepository);
    }

    @Test
    @DisplayName("⚠ 정본을 적재한 뒤에 DONE 으로 닫는다 — 순서가 뒤집히면 빈 전사가 분석된다")
    void 적재가_DONE보다_먼저다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.completed(words()));
        when(transcriptRepository.replaceBlockTranscript(anyLong(), anyInt(), anyList())).thenReturn(2);
        when(sttBlockRepository.markDone(BLOCK_ID)).thenReturn(true);

        int settled = service().pollOnce();

        /*
         * 먼저 닫으면 AnalysisOrchestrator 의 미완 블록 검사가 통과하고, 그 순간 분석이 시작되면
         * 앞부분만 있는 정본으로 요약·배정이 만들어져 "분석 완료"로 기록된다.
         */
        InOrder order = inOrder(transcriptRepository, sttBlockRepository);
        order.verify(transcriptRepository).replaceBlockTranscript(eq(MEETING), eq(BLOCK_SEQ), anyList());
        order.verify(sttBlockRepository).markDone(BLOCK_ID);
        assertThat(settled).isEqualTo(1);
    }

    @Test
    @DisplayName("⚠ 오프셋을 회의 기준으로 옮긴다 — 안 옮기면 두 번째 블록부터 맨 앞에 겹쳐 쌓인다")
    void 오프셋을_회의_기준으로_옮긴다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.completed(words()));
        when(transcriptRepository.replaceBlockTranscript(anyLong(), anyInt(), anyList())).thenReturn(1);

        List<NewUtterance> captured = captureUtterances();

        service().pollOnce();

        // 제공자는 블록의 처음을 0 으로 준다. 블록 시작(1,794,000ms)이 더해져야 회의 좌표다.
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).startOffsetMs()).isEqualTo(BLOCK_START_MS + 0);
        assertThat(captured.get(0).endOffsetMs()).isEqualTo(BLOCK_START_MS + 900);
    }

    @Test
    @DisplayName("도는 중이면 RUNNING 으로 옮기고 끝맺지 않는다")
    void 도는_중이면_RUNNING으로_옮긴다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.of(State.RUNNING));

        assertThat(service().pollOnce()).isZero();

        verify(sttBlockRepository).markRunning(BLOCK_ID);
        verify(sttBlockRepository, never()).markDone(anyLong());
        verify(transcriptRepository, never()).replaceBlockTranscript(anyLong(), anyInt(), anyList());
    }

    @Test
    @DisplayName("아직 큐에 있으면 아무것도 하지 않는다")
    void 큐에_있으면_그대로_둔다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.of(State.QUEUED));

        assertThat(service().pollOnce()).isZero();

        verify(sttBlockRepository, never()).markRunning(anyLong());
        verify(sttBlockRepository, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("⚠ 못 읽었으면 상태를 바꾸지 않는다 — 실패로 접으면 재처리가 요금을 두 번 태운다")
    void 못_읽으면_상태를_바꾸지_않는다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.of(State.UNAVAILABLE));

        assertThat(service().pollOnce()).isZero();

        verify(sttBlockRepository, never()).markFailed(anyLong(), anyString());
        verify(sttBlockRepository, never()).markDone(anyLong());
        verify(sttBlockRepository, never()).markRunning(anyLong());
    }

    @Test
    @DisplayName("제공자가 실패로 닫으면 FAILED — 사유 코드를 그대로 넘긴다")
    void 제공자_실패는_FAILED로_닫는다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.failed("JOB_FAILED"));
        when(sttBlockRepository.markFailed(BLOCK_ID, "JOB_FAILED")).thenReturn(true);

        assertThat(service().pollOnce()).isEqualTo(1);

        // FAILED 여야 STT-04 의 대상이 된다.
        verify(sttBlockRepository).markFailed(BLOCK_ID, "JOB_FAILED");
    }

    @Test
    @DisplayName("제공자가 모르는 잡은 FAILED 로 닫는다 — 그대로 두면 영원히 안 움직인다")
    void 모르는_잡은_FAILED로_닫는다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.of(State.UNKNOWN));
        when(sttBlockRepository.markFailed(anyLong(), anyString())).thenReturn(true);

        assertThat(service().pollOnce()).isEqualTo(1);

        verify(sttBlockRepository).markFailed(BLOCK_ID, "JOB_NOT_FOUND");
    }

    @Test
    @DisplayName("잡 이름이 없는 블록은 FAILED — 결과를 되짚을 방법이 없다")
    void 잡_이름이_없으면_FAILED다() {
        given(new PendingBlock(BLOCK_ID, MEETING, BLOCK_SEQ, SttBlockStatus.QUEUED,
                "aws-transcribe", null, BLOCK_START_MS));
        when(sttBlockRepository.markFailed(anyLong(), anyString())).thenReturn(true);

        assertThat(service().pollOnce()).isEqualTo(1);

        verify(sttBlockRepository).markFailed(BLOCK_ID, "NO_JOB_NAME");
        // 제공자를 부르지 않는다 — 부를 이름 자체가 없다.
        verify(sttJobResultPort, never()).fetch(anyString());
    }

    @Test
    @DisplayName("아직 제출 전(PENDING)인 블록은 건드리지 않는다 — 실패로 닫으면 제출이 막힌다")
    void 제출_전_블록은_건드리지_않는다() {
        given(new PendingBlock(BLOCK_ID, MEETING, BLOCK_SEQ, SttBlockStatus.PENDING,
                "aws-transcribe", null, BLOCK_START_MS));

        assertThat(service().pollOnce()).isZero();

        verify(sttBlockRepository, never()).markFailed(anyLong(), anyString());
        verify(sttJobResultPort, never()).fetch(anyString());
    }

    @Test
    @DisplayName("결과가 비어도 실패로 닫지 않는다 — 침묵 구간일 수 있다")
    void 결과가_비어도_DONE으로_닫는다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.completed(List.of()));
        when(transcriptRepository.replaceBlockTranscript(anyLong(), anyInt(), anyList())).thenReturn(0);
        when(sttBlockRepository.markDone(BLOCK_ID)).thenReturn(true);

        assertThat(service().pollOnce()).isEqualTo(1);

        verify(sttBlockRepository).markDone(BLOCK_ID);
        verify(sttBlockRepository, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("블록 하나가 터져도 나머지를 처리한다 — 워커가 죽으면 밀린 잡 전부가 멈춘다")
    void 블록_하나의_실패가_나머지를_막지_않는다() {
        PendingBlock broken = new PendingBlock(1L, MEETING, 1, SttBlockStatus.QUEUED,
                "aws-transcribe", "job-1", 0);
        PendingBlock healthy = new PendingBlock(2L, MEETING, 2, SttBlockStatus.QUEUED,
                "aws-transcribe", "job-2", 600_000);
        when(sttBlockRepository.findUnfinished(anyInt())).thenReturn(List.of(broken, healthy));

        when(sttJobResultPort.fetch("job-1")).thenThrow(new IllegalStateException("터짐"));
        when(sttJobResultPort.fetch("job-2")).thenReturn(SttJobOutcome.failed("JOB_FAILED"));
        when(sttBlockRepository.markFailed(2L, "JOB_FAILED")).thenReturn(true);

        assertThat(service().pollOnce()).isEqualTo(1);

        verify(sttJobResultPort, times(2)).fetch(anyString());
        verify(sttBlockRepository).markFailed(2L, "JOB_FAILED");
    }

    @Test
    @DisplayName("볼 블록이 없으면 제공자를 부르지 않는다")
    void 대상이_없으면_부르지_않는다() {
        when(sttBlockRepository.findUnfinished(anyInt())).thenReturn(List.of());

        assertThat(service().pollOnce()).isZero();

        verify(sttJobResultPort, never()).fetch(anyString());
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private void given(PendingBlock block) {
        when(sttBlockRepository.findUnfinished(anyInt())).thenReturn(List.of(block));
    }

    private static PendingBlock queued() {
        return new PendingBlock(BLOCK_ID, MEETING, BLOCK_SEQ, SttBlockStatus.QUEUED,
                "aws-transcribe", "meeting-500-block-3-r0", BLOCK_START_MS);
    }

    /* 블록 기준 오프셋 0~900ms 의 한 문장. */
    private static List<Word> words() {
        return List.of(
                new Word(0, 400, "로드맵", false),
                new Word(400, 900, "정리합시다", false),
                new Word(900, 900, ".", true));
    }

    @SuppressWarnings("unchecked")
    private List<NewUtterance> captureUtterances() {
        List<NewUtterance> captured = new ArrayList<>();
        when(transcriptRepository.replaceBlockTranscript(anyLong(), anyInt(), anyList()))
                .thenAnswer(invocation -> {
                    captured.addAll((List<NewUtterance>) invocation.getArgument(2));
                    return captured.size();
                });
        return captured;
    }
}
