package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenUsageRecordTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 12, 0);

    @Test
    void totalTokensAlwaysEqualsInputPlusOutput() {
        TokenUsageRecord record = TokenUsageRecord.create(1L, 10L, 100L, "job-1", 1200, 800, "m", NOW);

        // 불변식: 원장의 total 은 입력+출력과 항상 일치한다 — 방향 합계와 청구 total 이 갈리지 않는 근거.
        assertThat(record.getTotalTokens()).isEqualTo(record.getInputTokens() + record.getOutputTokens());
        assertThat(record.getTotalTokens()).isEqualTo(2000);
    }

    @Test
    void negativeTokensAreRejected() {
        assertThatThrownBy(() -> TokenUsageRecord.create(1L, 10L, 100L, "job-1", -1, 800, "m", NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MeteringErrorCode.MT_RECORD_COMMAND_INVALID));
    }
}
