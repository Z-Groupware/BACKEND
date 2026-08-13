package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.BillingPaymentActionResult;
import com.module06.backend.metering.application.result.BillingOverviewResult;
import com.module06.backend.metering.domain.exception.BillingErrorCode;
import com.module06.backend.metering.domain.model.BillingPaymentMethod;
import com.module06.backend.metering.domain.model.BillingPaymentRecord;
import com.module06.backend.metering.domain.model.BillingPaymentStatus;
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
    void payFailsWithValueResultWhenPaymentMethodIsMissing() {
        when(subscriptionRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(unpaidSubscription()));
        when(paymentMethodRepository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        BillingPaymentActionResult result = service.pay(owner());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureCode()).isEqualTo("NO_PAYMENT_METHOD");
        verify(subscriptionRepository, never()).save(any());
        verify(paymentRecordRepository, never()).save(any());
    }

    @Test
    void payIsIdempotentWhenAlreadyActive() {
        when(subscriptionRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(activeSubscription()));

        BillingPaymentActionResult result = service.pay(owner());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.failureCode()).isNull();
        verify(paymentMethodRepository, never()).findByCompanyId(any());
        verify(subscriptionRepository, never()).save(any());
        verify(paymentRecordRepository, never()).save(any());
    }

    @Test
    void payActivatesSubscriptionAndRecordsEstimatedAmount() {
        when(subscriptionRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(unpaidSubscription()));
        when(paymentMethodRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(paymentMethod()));
        when(tokenPlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(CompanyTokenPlan.restore(
                1L, COMPANY, "STANDARD", 1_500_000L, 150_000, 20, 10, 30,
                LocalDate.of(2026, 1, 1))));
        when(storagePlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());
        when(billingConfigRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(CompanyBillingConfig.restore(
                1L, COMPANY, false, 500)));

        BillingPaymentActionResult result = service.pay(owner());

        assertThat(result.isSuccess()).isTrue();

        ArgumentCaptor<BillingSubscription> subscriptionCaptor = ArgumentCaptor.forClass(BillingSubscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        BillingSubscription savedSubscription = subscriptionCaptor.getValue();
        assertThat(savedSubscription.getStatus()).isEqualTo(BillingSubscriptionStatus.ACTIVE);
        assertThat(savedSubscription.getCurrentPeriodStart()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(savedSubscription.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(savedSubscription.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 9, 12));

        ArgumentCaptor<BillingPaymentRecord> paymentCaptor = ArgumentCaptor.forClass(BillingPaymentRecord.class);
        verify(paymentRecordRepository).save(paymentCaptor.capture());
        BillingPaymentRecord payment = paymentCaptor.getValue();
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(167_200));
        assertThat(payment.getOverageAmount()).isEqualByComparingTo(BigDecimal.valueOf(2_000));
        assertThat(payment.getStatus()).isEqualTo(BillingPaymentStatus.PAID);
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
}
