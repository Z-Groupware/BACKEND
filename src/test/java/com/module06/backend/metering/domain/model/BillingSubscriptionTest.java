package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.BillingErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingSubscriptionTest {

    @Test
    void rejectsCurrentPeriodEndBeforeStart() {
        assertThatThrownBy(() -> BillingSubscription.create(1L, "TEAM", 0,
                BillingSubscriptionStatus.ACTIVE,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 9),
                LocalDate.of(2026, 9, 10),
                0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(BillingErrorCode.BIL_SUBSCRIPTION_COMMAND_INVALID));
    }
}
