package com.module06.backend.capture.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import com.module06.backend.capture.application.port.out.SttGapRepository;
import com.module06.backend.capture.application.port.out.SttCompletionEventPublisher;
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

    @Mock
    private SttGapRepository sttGapRepository;

    @Mock
    private SttCompletionEventPublisher sttCompletionEventPublisher;

    /* 갇힌 시간을 재는 기준이라 시계를 고정한다 — 테스트가 실행 시각에 따라 흔들리면 안 된다. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 15, 16, 0, 0);

    private SttResultPollingService service() {
        return new SttResultPollingService(
                sttBlockRepository, sttJobResultPort, transcriptRepository, sttGapRepository,
                sttCompletionEventPublisher,
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
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
    @DisplayName("길이를 모르는 블록은 인식 결과로 끝을 채운다 — 수동 업로드(WHOLE_FILE) 경로")
    void 길이를_모르면_복구한다() {
        /*
         * 수동 업로드(CAP-10)는 업로드 시점에 길이를 모른다 — 파일 하나가 통째로 블록 하나이고
         * endOffsetMs 가 0 이다. 그 0 이 남으면 CAP-06 의 남은 시간 추정이 이 블록을
         * "오디오 0초"로 세어 비율이 망가진다.
         */
        given(new PendingBlock(BLOCK_ID, MEETING, 0, SttBlockStatus.QUEUED,
                "aws-transcribe", "meeting-500-block-0-r0", 0, 0, NOW.minusMinutes(1)));
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.completed(words()));
        when(transcriptRepository.replaceBlockTranscript(anyLong(), anyInt(), anyList())).thenReturn(1);
        when(sttBlockRepository.markDone(BLOCK_ID)).thenReturn(true);

        service().pollOnce();

        // 마지막 단어의 끝(900ms)이 곧 그 오디오의 길이다.
        verify(sttBlockRepository).recoverAudioSpan(BLOCK_ID, 900);
    }

    @Test
    @DisplayName("구간을 아는 블록은 복구하지 않는다 — 인식 결과로 덮으면 블록 경계가 움직인다")
    void 구간을_알면_복구하지_않는다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.completed(words()));
        when(transcriptRepository.replaceBlockTranscript(anyLong(), anyInt(), anyList())).thenReturn(1);
        when(sttBlockRepository.markDone(BLOCK_ID)).thenReturn(true);

        service().pollOnce();

        /*
         * 자동 블록의 구간은 VAD 절단점이 정한 사실이다. 덮으면 뒤 블록의 시작과 맞지 않게 되고,
         * 그 어긋남이 정본 오프셋에 그대로 실린다.
         */
        verify(sttBlockRepository, never()).recoverAudioSpan(anyLong(), anyInt());
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
    @DisplayName("2026-08-15 — 못 읽는 상태가 30분을 넘기면 실패로 닫는다, 그대로 두면 영원히 QUEUED 다")
    void 오래_못_읽으면_실패로_닫는다() {
        /*
         * 회의 15 가 이 상태였다. 15초마다 같은 실패를 반복하는데 상태는 QUEUED 그대로라
         * DB 만 보면 "STT 진행 중"으로 읽히고, 그 위의 분석은 미완 블록 때문에 시작하지 않는다.
         * 회의가 통째로 멈춘 채 아무도 모른다.
         */
        given(queued(NOW.minusMinutes(31)));
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.of(State.UNAVAILABLE));
        when(sttBlockRepository.markFailed(BLOCK_ID, "RESULT_UNREACHABLE")).thenReturn(true);

        assertThat(service().pollOnce()).isEqualTo(1);

        verify(sttBlockRepository).markFailed(BLOCK_ID, "RESULT_UNREACHABLE");
        // 구멍을 남겨야 분배 확정이 막히고 "요약이 중단된 회의"에 올라온다.
        verify(sttGapRepository).replaceSttFailureGap(anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("아직 30분이 안 됐으면 기다린다 — 흔들린 것을 실패로 접으면 요금이 두 번 난다")
    void 임계_전에는_기다린다() {
        given(queued(NOW.minusMinutes(29)));
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.of(State.UNAVAILABLE));

        assertThat(service().pollOnce()).isZero();

        verify(sttBlockRepository, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("제출 시각을 모르면 포기하지 않는다 — 추측으로 닫으면 멀쩡한 잡을 실패로 만든다")
    void 제출_시각이_없으면_기다린다() {
        given(queued(null));
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.of(State.UNAVAILABLE));

        assertThat(service().pollOnce()).isZero();

        verify(sttBlockRepository, never()).markFailed(anyLong(), anyString());
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
    @DisplayName("⚠ 실패하면 그 구간을 구멍으로 남긴다 — 안 남기면 분배가 그대로 열려 있다")
    void 실패_구간을_구멍으로_남긴다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.failed("JOB_FAILED"));
        when(sttBlockRepository.markFailed(anyLong(), anyString())).thenReturn(true);

        service().pollOnce();

        /*
         * FAILED 만 남기면 그 구간은 "아무도 못 들은 10분"인데 분배 확정(RVW-05)은 열려 있다.
         * 사람은 회의가 온전히 처리된 줄 알고 확정하고, 그 구간의 할 일은 영구히 사라진다.
         */
        verify(sttGapRepository).replaceSttFailureGap(
                MEETING, BLOCK_SEQ, BLOCK_START_MS, BLOCK_START_MS + 600_000);
    }

    @Test
    @DisplayName("전이가 실패하면 구멍도 남기지 않는다 — 재처리로 되돌린 블록에 구멍이 붙는다")
    void 전이가_실패하면_구멍도_안_남긴다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.failed("JOB_FAILED"));
        // 그 사이 사람이 재처리를 눌러 QUEUED 로 되돌렸다.
        when(sttBlockRepository.markFailed(anyLong(), anyString())).thenReturn(false);

        assertThat(service().pollOnce()).isZero();

        verify(sttGapRepository, never()).replaceSttFailureGap(anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("⚠ 재처리가 성공하면 구멍을 지운다 — 안 지우면 분배가 영구히 막힌다")
    void 성공하면_구멍을_지운다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.completed(words()));
        when(transcriptRepository.replaceBlockTranscript(anyLong(), anyInt(), anyList())).thenReturn(1);
        when(sttBlockRepository.markDone(BLOCK_ID)).thenReturn(true);

        service().pollOnce();

        /*
         * 한 번 실패한 블록이 나중에 성공해도 구멍이 남으면 분배가 영구히 막히고, 사람은
         * STT-03 에서 그 블록이 DONE 인 것을 보면서 "왜 확정이 안 되지"를 묻게 된다.
         */
        verify(sttGapRepository).clearSttFailureGap(MEETING, BLOCK_SEQ);
    }

    @Test
    @DisplayName("마지막 블록까지 전부 DONE이면 자동 분석 완료 신호를 발행한다")
    void 전체_STT가_성공하면_완료_신호를_발행한다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.completed(words()));
        when(transcriptRepository.replaceBlockTranscript(anyLong(), anyInt(), anyList())).thenReturn(1);
        when(sttBlockRepository.markDone(BLOCK_ID)).thenReturn(true);
        when(sttBlockRepository.areAllDone(MEETING)).thenReturn(true);

        service().pollOnce();

        verify(sttCompletionEventPublisher).publish(
                new com.module06.backend.capture.application.event.SttTranscriptCompletedEvent(MEETING));
    }

    @Test
    @DisplayName("미완 또는 FAILED 블록이 남으면 자동 분석 완료 신호를 발행하지 않는다")
    void 전체_STT가_성공하지_않으면_완료_신호를_발행하지_않는다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.completed(words()));
        when(transcriptRepository.replaceBlockTranscript(anyLong(), anyInt(), anyList())).thenReturn(1);
        when(sttBlockRepository.markDone(BLOCK_ID)).thenReturn(true);
        when(sttBlockRepository.areAllDone(MEETING)).thenReturn(false);

        service().pollOnce();

        verify(sttCompletionEventPublisher, never()).publish(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("구멍 기록이 터져도 실패 처리는 유지한다 — 워커 로그에 미처리로 보이면 안 된다")
    void 구멍_기록_실패가_실패_처리를_뒤집지_않는다() {
        given(queued());
        when(sttJobResultPort.fetch(anyString())).thenReturn(SttJobOutcome.failed("JOB_FAILED"));
        when(sttBlockRepository.markFailed(anyLong(), anyString())).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("DB 흔들림"))
                .when(sttGapRepository).replaceSttFailureGap(anyLong(), anyInt(), anyInt(), anyInt());

        // 블록은 이미 FAILED 로 닫혔다. 여기서 예외를 올리면 그 사실이 로그에서 사라진다.
        assertThat(service().pollOnce()).isEqualTo(1);
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
                "aws-transcribe", null, BLOCK_START_MS, BLOCK_START_MS + 600_000, NOW.minusMinutes(1)));
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
                "aws-transcribe", null, BLOCK_START_MS, BLOCK_START_MS + 600_000, NOW.minusMinutes(1)));

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
                "aws-transcribe", "job-1", 0, 0 + 600_000, NOW.minusMinutes(1));
        PendingBlock healthy = new PendingBlock(2L, MEETING, 2, SttBlockStatus.QUEUED,
                "aws-transcribe", "job-2", 600_000, 600_000 + 600_000, NOW.minusMinutes(1));
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
        return queued(NOW.minusMinutes(1));
    }

    /* 제출 시각을 정해 만든다 — "결과에 닿지 못한 채 얼마나 갇혔나"를 세는 기준이다. */
    private static PendingBlock queued(LocalDateTime createdAt) {
        return new PendingBlock(BLOCK_ID, MEETING, BLOCK_SEQ, SttBlockStatus.QUEUED,
                "aws-transcribe", "meeting-500-block-3-r0", BLOCK_START_MS, BLOCK_START_MS + 600_000,
                createdAt);
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
