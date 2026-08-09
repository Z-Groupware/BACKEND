package com.module06.backend.capture.application.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.port.out.TranscriptRepository.UtteranceView;
import com.module06.backend.capture.application.usecase.GetTranscriptsUseCase.TranscriptPage;
import com.module06.backend.capture.domain.model.SpeakerSource;
import com.module06.backend.capture.domain.model.TranscriptCursor;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ANLZ-05 · 정본 스크립트 조회.
 *
 * <p>이 API 는 회의에서 오간 <b>말 전문</b>을 내보낸다. 캡처 파이프라인에서 유출 시 피해가 가장
 * 큰 자리라 회사 스코프 관문이 조회보다 먼저 서는지를 가장 앞에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class TranscriptQueryServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final int PAGE_SIZE = 200;

    @Mock
    private TranscriptRepository transcriptRepository;

    private final TranscriptCursorCodec codec = new TranscriptCursorCodec();

    private TranscriptQueryService service(boolean accessible) {
        return new TranscriptQueryService(
                transcriptRepository, codec, new MeetingAccessGuard((companyId, meetingId) -> accessible));
    }

    @Test
    @DisplayName("다른 회사 회의면 정본을 한 건도 읽지 않는다")
    void 관문이_조회보다_먼저_선다() {
        assertThatThrownBy(() -> service(false).getTranscripts(COMPANY, MEETING, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.MEETING_NOT_ACCESSIBLE);

        // 관문이 던진 뒤에도 조회가 돌면 예외와 무관하게 원문이 메모리에 올라온다.
        verifyNoInteractions(transcriptRepository);
    }

    @Test
    @DisplayName("페이지가 가득 차면 마지막 발화로 다음 커서를 만든다")
    void 가득_찬_페이지는_다음_커서를_준다() {
        List<UtteranceView> full = page(PAGE_SIZE, 1000, 300);
        when(transcriptRepository.findPage(eq(MEETING), isNull(), eq(PAGE_SIZE))).thenReturn(full);

        TranscriptPage result = service(true).getTranscripts(COMPANY, MEETING, null, null);

        assertThat(result.nextCursor()).isNotNull();
        UtteranceView last = full.get(full.size() - 1);
        assertThat(codec.decode(result.nextCursor()))
                .isEqualTo(new TranscriptCursor(last.startOffsetMs(), last.seq()));
    }

    @Test
    @DisplayName("페이지가 덜 찼으면 마지막 페이지다 — 다음 커서가 없다")
    void 덜_찬_페이지는_커서가_없다() {
        when(transcriptRepository.findPage(eq(MEETING), isNull(), eq(PAGE_SIZE)))
                .thenReturn(page(3, 1000, 300));

        TranscriptPage result = service(true).getTranscripts(COMPANY, MEETING, null, null);

        assertThat(result.utterances()).hasSize(3);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("받은 커서를 해석해 그 자리 다음부터 읽는다")
    void 커서를_그대로_저장소에_넘긴다() {
        TranscriptCursor cursor = new TranscriptCursor(623400, 372);
        when(transcriptRepository.findPage(MEETING, cursor, PAGE_SIZE)).thenReturn(List.of());

        service(true).getTranscripts(COMPANY, MEETING, codec.encode(cursor), null);

        // 커서를 흘리면 매번 첫 페이지가 나가 같은 발화가 반복된다.
        verify(transcriptRepository).findPage(MEETING, cursor, PAGE_SIZE);
    }

    @Test
    @DisplayName("ids 를 주면 그것만 돌려주고 페이지를 나누지 않는다")
    void ids_는_커서를_쓰지_않는다() {
        List<Long> ids = List.of(8842L, 8845L);
        when(transcriptRepository.findByMeetingAndIds(MEETING, ids)).thenReturn(page(2, 10, 1));

        TranscriptPage result = service(true).getTranscripts(COMPANY, MEETING, null, ids);

        assertThat(result.utterances()).hasSize(2);
        // 요청한 것이 전부라 이어질 페이지가 없다.
        assertThat(result.nextCursor()).isNull();
        verify(transcriptRepository, never()).findPage(anyLong(), org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    @DisplayName("ids 가 상한을 넘으면 400 이다 — 페이징을 우회하는 경로가 된다")
    void ids_상한을_넘으면_막는다() {
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> service(true).getTranscripts(COMPANY, MEETING, null, tooMany))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.TRANSCRIPT_IDS_TOO_MANY);

        verifyNoInteractions(transcriptRepository);
    }

    @Test
    @DisplayName("빈 ids 는 지정이 없는 것과 같다 — 첫 페이지를 준다")
    void 빈_ids_는_페이지_조회로_간다() {
        when(transcriptRepository.findPage(eq(MEETING), isNull(), eq(PAGE_SIZE))).thenReturn(page(1, 10, 1));

        // ?ids= 를 빈 값으로 붙이는 클라이언트가 있다. 그걸 "0건 요청"으로 읽으면 화면이 빈다.
        TranscriptPage result = service(true).getTranscripts(COMPANY, MEETING, null, List.of());

        assertThat(result.utterances()).hasSize(1);
    }

    private static List<UtteranceView> page(int size, int firstOffsetMs, int firstSeq) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> new UtteranceView(
                        1000L + i,
                        firstSeq + i,
                        i % 2 == 0 ? 7L : null,
                        i % 2 == 0 ? SpeakerSource.SELF_STREAM : null,
                        firstOffsetMs + (i * 100),
                        firstOffsetMs + (i * 100) + 50,
                        "발화 " + i))
                .toList();
    }
}
