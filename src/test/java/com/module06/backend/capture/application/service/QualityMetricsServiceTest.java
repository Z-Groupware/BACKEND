package com.module06.backend.capture.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.out.QualityMetricsRepository;
import com.module06.backend.capture.application.usecase.GetQualityMetricsUseCase.QualityMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * QLTY-02 · 품질 지표.
 *
 * <p>검증의 축은 <b>무엇을 성공으로 세는가</b>다. 같은 데이터로도 MODIFY 를 성공으로 볼지,
 * 사람이 직접 추가한 액션을 놓친 것으로 볼지에 따라 숫자가 통째로 바뀐다 — 그 판단이 흔들리면
 * 프롬프트를 고쳐야 할지 게이트를 조여야 할지가 정반대로 읽힌다.
 *
 * <p>그리고 <b>잴 수 없는 것을 0 으로 답하지 않는가</b>다. 0.0 은 "다 틀렸다"이고 null 은
 * "표본이 없다"인데, 뭉치면 정답지를 안 만든 상태가 모델이 완전히 실패한 것으로 보인다.
 */
class QualityMetricsServiceTest {

    private static final long COMPANY = 7L;

    @Test
    @DisplayName("MODIFY 는 성공으로 센다 — 담당자를 고쳤어도 '그 일이 있다'는 판정은 맞았다")
    void 수정은_성공으로_센다() {
        // AI 가 만든 10건 중 8건 인정(CONFIRM·MODIFY) · 2건 반려.
        QualityMetrics metrics = service(tally(8, 2, 0, 0, 0, 0)).getMetrics(COMPANY);

        assertThat(metrics.precision()).isCloseTo(0.8, within(0.001));
    }

    @Test
    @DisplayName("사람이 직접 추가한 액션이 recall 의 놓친 것이다 — 명확한데 AI 가 못 잡은 것")
    void 직접_추가가_놓친_것이다() {
        // AI 가 8건 맞히고, 사람이 2건을 손으로 넣었다 = 실제 10건 중 8건.
        QualityMetrics metrics = service(tally(8, 0, 2, 0, 0, 0)).getMetrics(COMPANY);

        assertThat(metrics.recall()).isCloseTo(0.8, within(0.001));
        // 직접 추가는 AI 가 만든 것이 아니라 precision 의 분모에 들어가면 안 된다.
        assertThat(metrics.precision()).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("게이트 오류율은 자동 확정한 것 중 사람이 고친 비율이다")
    void 게이트_오류율() {
        QualityMetrics metrics = service(tally(10, 0, 0, 20, 1, 50)).getMetrics(COMPANY);

        assertThat(metrics.autoConfirmErrorRate()).isCloseTo(0.05, within(0.001));
    }

    @Test
    @DisplayName("needsReviewRate 는 자동 확정되지 않은 비율이다 — 품질이 아니라 비용 지표다")
    void 검토_필요_비율() {
        // tuple 50건 중 20건 자동 확정 → 30건을 사람이 봐야 한다.
        QualityMetrics metrics = service(tally(10, 0, 0, 20, 0, 50)).getMetrics(COMPANY);

        assertThat(metrics.needsReviewRate()).isCloseTo(0.6, within(0.001));
    }

    @Test
    @DisplayName("표본이 없으면 비율은 null 이다 — 0.0 이면 '다 틀렸다'로 읽힌다")
    void 표본이_없으면_null이다() {
        QualityMetrics metrics = service(tally(0, 0, 0, 0, 0, 0)).getMetrics(COMPANY);

        assertThat(metrics.precision()).isNull();
        assertThat(metrics.recall()).isNull();
        assertThat(metrics.autoConfirmErrorRate()).isNull();
        assertThat(metrics.needsReviewRate()).isNull();
    }

    @Test
    @DisplayName("자동 확정이 하나도 없으면 게이트 오류율은 null 이다 — 0 은 '게이트가 완벽하다'로 읽힌다")
    void 자동확정이_없으면_게이트_오류율은_null이다() {
        QualityMetrics metrics = service(tally(10, 0, 0, 0, 0, 50)).getMetrics(COMPANY);

        assertThat(metrics.autoConfirmErrorRate()).isNull();
        // tuple 은 있으므로 검토 필요 비율은 잴 수 있다(전부 사람이 봐야 한다).
        assertThat(metrics.needsReviewRate()).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("정답 액션 수를 함께 준다 — 지표의 신뢰 구간이다")
    void 정답_액션_수를_준다() {
        QualityMetrics metrics = service(tally(8, 2, 2, 0, 0, 0)).getMetrics(COMPANY);

        // 8 + 2 + 2 = 12. 5건으로 잰 0.8 과 100건으로 잰 0.8 은 같은 값이 아니다.
        assertThat(metrics.goldSet().actionCount()).isEqualTo(12);
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private QualityMetricsService service(QualityMetricsRepository.MetricsTally tally) {
        return new QualityMetricsService(companyId -> tally);
    }

    private static QualityMetricsRepository.MetricsTally tally(
            int aiValid, int aiRejected, int manualAdded,
            int autoConfirmed, int autoConfirmedWrong, int tupleCount) {
        int reviewed = aiValid + aiRejected + manualAdded;
        return new QualityMetricsRepository.MetricsTally(
                reviewed > 0 ? 3 : 0, reviewed, aiValid, aiRejected, manualAdded,
                autoConfirmed, autoConfirmedWrong, tupleCount, "gemini-3.5-flash", "v3");
    }
}
