package com.module06.backend.capture.application.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LayerState;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.application.port.out.SttGapRepository;
import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.application.usecase.ResumeAnalysisUseCase.ResumeOutcome;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ANLZ-02 · 계층 재개의 관문.
 *
 * <p>오케스트레이터는 "앞 계층을 부르지 않는다"만 한다. <b>앞 계층이 실제로 끝나 있는가</b>는
 * 이 서비스가 본다 — 끝나지 않은 계층의 산출물을 되살리려 하면 빈 문맥으로 모델을 부르게 되고,
 * 그 빈 결과가 DONE 으로 기록돼 조회는 "분석 완료"라고 말한다. 토큰은 토큰대로 쓰고 실패는
 * 감춰지는, 이 파이프라인에서 가장 위험한 조합이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeAnalysisServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;

    @Mock
    private AnalysisOrchestrator orchestrator;

    @Mock
    private AnalysisLayerRepository analysisLayerRepository;

    @Mock
    private MeetingSummaryRepository meetingSummaryRepository;

    @Mock
    private SttGapRepository sttGapRepository;

    @Mock
    private SttBlockRepository sttBlockRepository;

    /* 구멍·블록 저장소는 CAP-06 만 쓴다 — 재개(ANLZ-02) 경로는 건드리지 않는다. */
    private AnalysisService service(boolean accessible) {
        return new AnalysisService(
                orchestrator, analysisLayerRepository, meetingSummaryRepository,
                meetingId -> List.of(),
                sttGapRepository, sttBlockRepository,
                new MeetingAccessGuard((companyId, meetingId) -> accessible));
    }

    @Test
    @DisplayName("다른 회사 회의면 재개하지 않는다 — 관문이 계층 조회보다 먼저 선다")
    void 관문이_먼저_선다() {
        assertThatThrownBy(() -> service(false).resume(COMPANY, MEETING, "L4"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.MEETING_NOT_ACCESSIBLE);

        verifyNoInteractions(analysisLayerRepository, orchestrator);
    }

    @Test
    @DisplayName("앞 계층이 전부 DONE 이면 그 계층부터 재개한다")
    void 앞_계층이_끝났으면_재개한다() {
        when(analysisLayerRepository.findStates(MEETING)).thenReturn(doneUpTo(LayerName.L3_5));
        when(orchestrator.run(anyLong(), anyLong(), anyLong(), any(), anyBoolean(), eq(LayerName.L4)))
                .thenReturn(AnalysisOutcome.skipped("테스트"));

        ResumeOutcome resumed = service(true).resume(COMPANY, MEETING, "L4");

        assertThat(resumed.resumeFrom()).isEqualTo(LayerName.L4);
        assertThat(resumed.reusedLayers())
                .containsExactly(LayerName.L1, LayerName.L1_5, LayerName.L2, LayerName.L3, LayerName.L3_5);

        /*
         * force 를 켜지 않는다. 재개 경로는 "이미 완료" 판정을 아예 지나지 않으므로 켤 이유가
         * 없고, 켜면 그 뜻이 "재과금을 감수한다"로 읽힌다 — 재개는 정확히 그 반대다.
         */
        verify(orchestrator).run(COMPANY, COMPANY, MEETING, List.of(), false, LayerName.L4);
    }

    @Test
    @DisplayName("앞 계층이 안 끝났으면 409 — 문맥 없이 부르면 빈 결과가 완료로 기록된다")
    void 앞_계층이_안_끝났으면_막는다() {
        // L3 까지만 끝났는데 L4 부터 재개하려 한다. L3.5 판정이 없으면 확정된 항목이 없다.
        when(analysisLayerRepository.findStates(MEETING)).thenReturn(doneUpTo(LayerName.L3));

        assertThatThrownBy(() -> service(true).resume(COMPANY, MEETING, "L4"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .satisfies(code -> {
                    assertThat(code).isEqualTo(CaptureErrorCode.RESUME_PRECEDING_LAYER_NOT_DONE);
                    assertThat(code.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(orchestrator, never()).run(anyLong(), anyLong(), anyLong(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("FAILED 는 DONE 이 아니다 — 실패한 계층을 앞 계층으로 세면 안 된다")
    void 실패한_계층은_되살릴_수_없다() {
        // L2 가 FAILED 인데 L4 부터 재개하려 한다. 되살릴 주제 묶음이 없다.
        List<LayerState> states = List.of(
                new LayerState(LayerName.L1, LayerStatus.DONE, 0, 0, false),
                new LayerState(LayerName.L1_5, LayerStatus.DONE, 0, 0, false),
                new LayerState(LayerName.L2, LayerStatus.FAILED, 0, 0, false));
        when(analysisLayerRepository.findStates(MEETING)).thenReturn(states);

        assertThatThrownBy(() -> service(true).resume(COMPANY, MEETING, "L4"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.RESUME_PRECEDING_LAYER_NOT_DONE);
    }

    @Test
    @DisplayName("L1 부터 재개하면 확인할 앞 계층이 없다")
    void 처음부터_재개하면_앞_계층_검사가_없다() {
        when(analysisLayerRepository.findStates(MEETING)).thenReturn(List.of());
        when(orchestrator.run(anyLong(), anyLong(), anyLong(), any(), anyBoolean(), eq(LayerName.L1)))
                .thenReturn(AnalysisOutcome.skipped("테스트"));

        ResumeOutcome resumed = service(true).resume(COMPANY, MEETING, "L1");

        // 되살릴 앞 계층이 없으니 DONE 검사가 통과를 막지 않는다.
        assertThat(resumed.reusedLayers()).isEmpty();
        verify(orchestrator).run(COMPANY, COMPANY, MEETING, List.of(), false, LayerName.L1);
    }

    @Test
    @DisplayName("도는 중이면 재개하지 않는다 — 진행 중 실행이 태운 토큰이 버려진다")
    void 실행_중에는_재개하지_않는다() {
        // L2 가 아직 RUNNING 이다.
        List<LayerState> running = List.of(
                new LayerState(LayerName.L1, LayerStatus.DONE, 0, 0, false),
                new LayerState(LayerName.L2, LayerStatus.RUNNING, 0, 0, false));
        when(analysisLayerRepository.findStates(MEETING)).thenReturn(running);

        /*
         * 막지 않으면 재개가 새 runSeq 를 발급하고, 진행 중이던 실행은 다음 계층 잠금에서
         * SUPERSEDED 로 물러난다(#134) — 그 실행이 이미 태운 토큰이 버려진다.
         * 재과금을 줄이려고 만든 API 가 정확히 반대로 동작하는 경로다.
         */
        assertThatThrownBy(() -> service(true).resume(COMPANY, MEETING, "L4"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.ANALYSIS_ALREADY_RUNNING);

        verify(orchestrator, never()).run(anyLong(), anyLong(), anyLong(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("모르는 계층 이름은 400 — 기본값으로 넘기면 엉뚱한 곳에서 재개된다")
    void 모르는_계층은_400_이다() {
        assertThatThrownBy(() -> service(true).resume(COMPANY, MEETING, "L9"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .satisfies(code -> {
                    assertThat(code).isEqualTo(CaptureErrorCode.RESUME_LAYER_UNKNOWN);
                    assertThat(code.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("계층 이름은 전송 값이다 — enum 상수 이름(L1_5)은 받지 않는다")
    void 계층_이름은_전송_값이다() {
        when(analysisLayerRepository.findStates(MEETING)).thenReturn(doneUpTo(LayerName.L1));
        when(orchestrator.run(anyLong(), anyLong(), anyLong(), any(), anyBoolean(), eq(LayerName.L1_5)))
                .thenReturn(AnalysisOutcome.skipped("테스트"));

        // DB·Python 계약이 "L1.5" 다. 둘을 섞으면 같은 계층이 두 이름으로 갈린다.
        assertThat(service(true).resume(COMPANY, MEETING, "L1.5").resumeFrom()).isEqualTo(LayerName.L1_5);

        assertThatThrownBy(() -> service(true).resume(COMPANY, MEETING, "L1_5"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.RESUME_LAYER_UNKNOWN);
    }

    @Test
    @DisplayName("빈 계층 이름은 400 이다")
    void 빈_계층_이름은_400_이다() {
        assertThatThrownBy(() -> service(true).resume(COMPANY, MEETING, "  "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.RESUME_LAYER_UNKNOWN);
    }

    /* 파이프라인 순서대로 그 계층까지 DONE 인 상태를 만든다. */
    private static List<LayerState> doneUpTo(LayerName last) {
        List<LayerName> order = List.of(LayerName.L1, LayerName.L1_5, LayerName.L2, LayerName.L3,
                LayerName.L3_5, LayerName.L4, LayerName.L5, LayerName.L6, LayerName.L7, LayerName.DIST);
        return order.subList(0, order.indexOf(last) + 1).stream()
                .map(layer -> new LayerState(layer, LayerStatus.DONE, 0, 0, false))
                .toList();
    }
}
