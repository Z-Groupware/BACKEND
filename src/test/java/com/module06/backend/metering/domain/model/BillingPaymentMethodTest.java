package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.BillingErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingPaymentMethodTest {

    @Test
    void createRejectsBlankBillingKey() {
        assertThatThrownBy(() -> BillingPaymentMethod.create(1L, "VISA", "1234",
                LocalDate.of(2029, 12, 1), " ", true))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(BillingErrorCode.BIL_PAYMENT_METHOD_COMMAND_INVALID));
    }

    @Test
    void restoreAllowsLegacyNullBillingKey() {
        BillingPaymentMethod method = BillingPaymentMethod.restore(1L, 2L, "VISA", "1234",
                LocalDate.of(2029, 12, 1), null, true);

        assertThat(method.getBillingKey()).isNull();
    }
}
