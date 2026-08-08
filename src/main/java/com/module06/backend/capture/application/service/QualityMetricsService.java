package com.module06.backend.capture.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.QualityMetricsRepository;
import com.module06.backend.capture.application.port.out.QualityMetricsRepository.MetricsTally;
import com.module06.backend.capture.application.usecase.GetQualityMetricsUseCase;

/*
 * QLTY-02 · 품질 지표 산출.
 *
 * <h2>비율의 뜻이 여기 있다</h2>
 * DB 는 개수만 세고, **무엇을 성공으로 보는가**는 이 클래스가 정한다. 그 판단이 SQL 안에 있으면
 * 나중에 지표가 이상할 때 왜 그렇게 세는지가 쿼리 문자열에 숨는다.
 *
 * <h2>잴 수 없으면 null 이다 — 0 이 아니다</h2>
 * 분모가 0 인데 0.0 을 돌려주면 "다 틀렸다"로 읽힌다. 정답지를 아직 안 만든 상태와 모델이
 * 완전히 실패한 상태가 같은 화면이 되는데, 그 둘은 해야 할 일이 정반대다.
 */
@Service
@RequiredArgsConstructor
public class QualityMetricsService implements GetQualityMetricsUseCase {

    private final QualityMetricsRepository qualityMetricsRepository;

    @Override
    @Transactional(readOnly = true)
    public QualityMetrics getMetrics(long companyId) {
        MetricsTally tally = qualityMetricsRepository.tally(companyId);

        return new QualityMetrics(
                new GoldSetSummary(tally.goldSetMeetingCount(), tally.reviewedActionCount()),
                precisionOf(tally),
                recallOf(tally),
                autoConfirmErrorRateOf(tally),
                needsReviewRateOf(tally),
                tally.promptVersion(),
                tally.model());
    }

    /*
     * AI 가 만든 것 중 실제로 액션이었던 비율.
     *
     * MODIFY 를 성공으로 센다 — 담당자를 고쳤어도 **"그 일이 있다"는 판정은 맞았다.** 필드
     * 정확도는 다른 축이고, 그걸 여기 섞으면 "액션을 지어냈다(hallucination)"와 "담당자를
     * 잘못 짚었다"가 한 숫자로 뭉쳐 무엇을 고쳐야 할지 가리키지 못한다.
     */
    private Double precisionOf(MetricsTally tally) {
        int predicted = tally.aiValidCount() + tally.aiRejectedCount();
        return ratio(tally.aiValidCount(), predicted);
    }

    /*
     * 실제 액션 중 AI 가 잡아낸 비율.
     *
     * 놓친 것(FN)은 **사람이 직접 추가한 액션**이다(RVW-03). 회의에 분명히 있었는데 AI 가 안
     * 만들어서 사람이 손으로 넣은 것이라, 그게 곧 "명확한데 못 잡은 것"의 정의다.
     *
     * ⚠ 회의에서 애매하게 말해 아무도 액션으로 만들지 않은 일은 여기 안 잡힌다. 그건 측정할
     * 방법이 없고(아무 기록도 남지 않는다) 시스템 실패도 아니다.
     */
    private Double recallOf(MetricsTally tally) {
        int actual = tally.aiValidCount() + tally.manualAddedCount();
        return ratio(tally.aiValidCount(), actual);
    }

    /*
     * 게이트가 "AI 확신도 높음"으로 자동 확정한 것 중 사람이 고치거나 반려한 비율.
     *
     * **이 값이 게이트의 성적표다.** 높으면 자동 확정 조건이 느슨한 것이고, 그건 사람이 안 보고
     * 지나간 액션이 그만큼 틀렸다는 뜻이라 곧바로 보드로 나간다.
     */
    private Double autoConfirmErrorRateOf(MetricsTally tally) {
        return ratio(tally.autoConfirmedWrong(), tally.autoConfirmedCount());
    }

    /*
     * 자동 확정되지 않아 사람이 봐야 하는 비율.
     *
     * ⚠ **품질 지표가 아니라 비용 지표다.** 줄어드는 이유가 둘이다 — 정말 정확해졌거나, 모델이
     * 과신하거나. 목표로 걸면 임계값을 낮춰 숫자를 맞추려는 유인이 생기고 "확신 없으면 비워 둘
     * 것" 원칙과 충돌한다(명세 QLTY-02 처리 정책).
     */
    private Double needsReviewRateOf(MetricsTally tally) {
        return ratio(tally.tupleCount() - tally.autoConfirmedCount(), tally.tupleCount());
    }

    /* 분모가 0 이면 null 이다 — "다 틀렸다"(0.0)와 "못 잰다"를 구분한다. */
    private Double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return (double) numerator / denominator;
    }
}
