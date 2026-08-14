package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.BillingPaymentActionResult;
import com.module06.backend.metering.application.result.BillingOverviewResult;
import com.module06.backend.metering.domain.exception.BillingErrorCode;
import com.module06.backend.metering.domain.model.BillingPaymentMethod;
import com.module06.backend.metering.domain.model.BillingPaymentRecord;
import com.module06.backend.metering.domain.model.BillingPaymentStatus;
import com.module06.backend.metering.domain.model.BillingDefaults;
import com.module06.backend.metering.domain.model.BillingSubscription;
import com.module06.backend.metering.domain.model.BillingSubscriptionStatus;
import com.module06.backend.metering.domain.model.CompanyBillingConfig;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.repository.BillingPaymentMethodRepository;
import com.module06.backend.metering.domain.repository.BillingPaymentRecordRepository;
import com.module06.backend.metering.domain.repository.BillingSubscriptionRepository;
import com.module06.backend.metering.domain.repository.CompanyBillingConfigRepository;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingCommandServiceTest {

    private static final Long COMPANY = 7L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-12T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private BillingSubscriptionRepository subscriptionRepository;

    @Mock
    private BillingPaymentMethodRepository paymentMethodRepository;

    @Mock
    private BillingPaymentRecordRepository paymentRecordRepository;

    @Mock
    private CompanyTokenPlanRepository tokenPlanRepository;

    @Mock
    private CompanyStoragePlanRepository storagePlanRepository;

    @Mock
    private CompanyBillingConfigRepository billingConfigRepository;

    private BillingCommandService service;

    @BeforeEach
    void setUp() {
        BillingConfigService billingConfigService =
                new BillingConfigService(tokenPlanRepository, storagePlanRepository, billingConfigRepository);
        service = new BillingCommandService(
                subscriptionRepository,
                paymentMethodRepository,
                paymentRecordRepository,
                billingConfigService,
                FIXED_CLOCK
        );
    }

    @Test
    void payReturnsDisplayOnlySuccessForBillingManager() {
        // [DEMO] 표시 전용 목업: 실 구독 활성화/결제레코드 저장 없이 항상 성공을 반환한다.
        BillingPaymentActionResult result = service.pay(owner());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.failureCode()).isNull();
        verify(subscriptionRepository, never()).save(any());
        verify(paymentRecordRepository, never()).save(any());
    }

    @Test
    void payRejectsNonBillingManager() {
        assertThatThrownBy(() -> service.pay(nonManager()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void registerPaymentMethodRejectsCrossCompanyCustomerKey() {
        assertThatThrownBy(() -> service.register(owner(), "mock_auth_1234", "8"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(BillingErrorCode.BIL_PAYMENT_METHOD_COMPANY_MISMATCH));
    }

    @Test
    void registerPaymentMethodReplacesExistingMethodAndDoesNotExposeBillingKey() {
        BillingPaymentMethod existing = BillingPaymentMethod.restore(
                3L, COMPANY, "VISA", "1111", LocalDate.of(2027, 9, 1), "old_billing_key", true);
        when(paymentMethodRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(existing));
        when(paymentMethodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BillingOverviewResult.PaymentMethodResult result = service.register(owner(), "mock_auth_5678", "7");

        ArgumentCaptor<BillingPaymentMethod> methodCaptor = ArgumentCaptor.forClass(BillingPaymentMethod.class);
        verify(paymentMethodRepository).save(methodCaptor.capture());
        BillingPaymentMethod saved = methodCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(3L);
        assertThat(saved.getBillingKey()).isNotBlank();
        assertThat(saved.getBillingKey()).isNotEqualTo("old_billing_key");
        assertThat(result.id()).isEqualTo(3L);
        assertThat(result.brand()).isEqualTo("MASTER");
        assertThat(result.last4()).isEqualTo("5678");
        assertThat(result.expiry()).isEqualTo("12/29");
    }

    private static BillingSubscription unpaidSubscription() {
        return BillingSubscription.restore(1L, COMPANY, "TEAM", 0, BillingSubscriptionStatus.UNPAID,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 1),
                null,
                null,
                2_000);
    }

    private static BillingSubscription activeSubscription() {
        return BillingSubscription.restore(1L, COMPANY, "TEAM", 0, BillingSubscriptionStatus.ACTIVE,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1),
                0);
    }

    private static BillingPaymentMethod paymentMethod() {
        return BillingPaymentMethod.restore(
                2L, COMPANY, "MASTER", "1234", LocalDate.of(2029, 12, 1), "mock_billing_key", true);
    }

    private static AuthPrincipal owner() {
        return new AuthPrincipal(1L, COMPANY, "OWNER", false, 10L);
    }

    private static AuthPrincipal nonManager() {
        return new AuthPrincipal(2L, COMPANY, "MEMBER", false, 11L);
    }
}
