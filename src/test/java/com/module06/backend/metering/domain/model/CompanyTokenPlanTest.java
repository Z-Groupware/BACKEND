package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyTokenPlanTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    @Test
    void directionalPricingReflectsSeparateInputAndOutputRates() {
        // 총량 단가 300, 입력 90 · 출력 360 (LLM 은 출력이 더 비싸다).
        CompanyTokenPlan plan = CompanyTokenPlan.create(1L, "STANDARD", 0L, 0, 300, 90, 360, FROM);

        long single = plan.usageAmountKrw(700_000L);              // ceil(700,000/1k)=700 * 300 = 210,000
        long directional = plan.usageAmountKrw(500_000L, 200_000L); // 500*90 + 200*360 = 45,000 + 72,000 = 117,000

        assertThat(single).isEqualTo(210_000L);
        assertThat(directional).isEqualTo(117_000L);
        // 같은 사용량이라도 방향 단가를 나누면 실제 비용 구조가 반영돼 금액이 달라진다.
        assertThat(directional).isNotEqualTo(single);
    }

    @Test
    void perDirectionRoundingDiffersEvenWhenRatesAreEqual() {
        // 세 단가가 모두 100원/1k 로 같아도, 방향별로 1k 올림을 따로 하면 총량 올림과 달라진다.
        CompanyTokenPlan plan = CompanyTokenPlan.create(1L, "STANDARD", 0L, 0, 100, 100, 100, FROM);

        long single = plan.usageAmountKrw(2_000L);           // ceil(2,000/1k)=2 * 100 = 200
        long directional = plan.usageAmountKrw(1_500L, 500L);// ceil(1,500/1k)=2 + ceil(500/1k)=1 → 3 * 100 = 300

        assertThat(single).isEqualTo(200L);
        assertThat(directional).isEqualTo(300L);
    }

    @Test
    void negativeDirectionalPriceIsRejected() {
        assertThatThrownBy(() -> CompanyTokenPlan.create(1L, "STANDARD", 0L, 0, 100, -1, 100, FROM))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MeteringErrorCode.MT_PLAN_COMMAND_INVALID));
    }
}
