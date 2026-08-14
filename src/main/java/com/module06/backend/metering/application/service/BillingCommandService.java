package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.BillingConfigResult;
import com.module06.backend.metering.application.result.BillingOverviewResult;
import com.module06.backend.metering.application.result.BillingPaymentActionResult;
import com.module06.backend.metering.application.usecase.ManageBillingPaymentMethodUseCase;
import com.module06.backend.metering.application.usecase.ManageBillingSubscriptionUseCase;
import com.module06.backend.metering.domain.exception.BillingErrorCode;
import com.module06.backend.metering.domain.model.BillingPaymentMethod;
import com.module06.backend.metering.domain.model.BillingPaymentRecord;
import com.module06.backend.metering.domain.model.BillingPaymentStatus;
import com.module06.backend.metering.domain.model.BillingDefaults;
import com.module06.backend.metering.domain.model.BillingSubscription;
import com.module06.backend.metering.domain.model.BillingSubscriptionStatus;
import com.module06.backend.metering.domain.repository.BillingPaymentMethodRepository;
import com.module06.backend.metering.domain.repository.BillingPaymentRecordRepository;
import com.module06.backend.metering.domain.repository.BillingSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class BillingCommandService implements ManageBillingSubscriptionUseCase, ManageBillingPaymentMethodUseCase {

    private static final Logger log = LoggerFactory.getLogger(BillingCommandService.class);

    private static final String OWNER_ROLE = "OWNER";
    private static final double VAT_RATE = 0.10;
    private static final String NO_PAYMENT_METHOD = "NO_PAYMENT_METHOD";

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPaymentMethodRepository paymentMethodRepository;
    private final BillingPaymentRecordRepository paymentRecordRepository;
    private final BillingConfigService billingConfigService;
    private final Clock clock;

    public BillingCommandService(BillingSubscriptionRepository subscriptionRepository,
                                 BillingPaymentMethodRepository paymentMethodRepository,
                                 BillingPaymentRecordRepository paymentRecordRepository,
                                 BillingConfigService billingConfigService,
                                 @Qualifier("meetingClock") Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.billingConfigService = billingConfigService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BillingPaymentActionResult pay(AuthPrincipal principal) {
        requireBillingManager(principal);
        // [DEMO] 실결제·구독 활성화는 미구현 상태다. 실제 DB 경로(구독 활성화/결제레코드 저장)에서
        // 발생하던 예외가 캐치올(Z-003, 500)로 노출되던 것을 데모용으로 우회한다.
        // FE 결제 버튼이 항상 "완료"로 표시되도록 표시 전용 성공만 반환한다.
        // TODO(billing): PG 연동·실 활성화 경로 복구 시 이 목업을 제거하고
        //   getOrCreateSubscription/activate/estimatedAmount 기반 원 로직으로 환원할 것.
        log.warn("[DEMO] pay() returns display-only success (real activation bypassed). companyId={}",
                principal.companyId());
        return BillingPaymentActionResult.success();
    }

    @Override
    @Transactional
    public BillingOverviewResult.PaymentMethodResult register(AuthPrincipal principal, String authKey, String customerKey) {
        requireBillingManager(principal);
        if (authKey == null || authKey.isBlank()) {
            throw new BusinessException(BillingErrorCode.BIL_PAYMENT_METHOD_COMMAND_INVALID);
        }
        if (customerKey == null || !customerKey.equals(String.valueOf(principal.companyId()))) {
            throw new BusinessException(BillingErrorCode.BIL_PAYMENT_METHOD_COMPANY_MISMATCH);
        }

        BillingPaymentMethod method = paymentMethodRepository.findByCompanyId(principal.companyId())
                .map(existing -> BillingPaymentMethod.restore(
                        existing.getId(),
                        principal.companyId(),
                        "MASTER",
                        mockLast4(authKey),
                        LocalDate.of(2029, 12, 1),
                        mockBillingKey(authKey, principal.companyId()),
                        true
                ))
                .orElseGet(() -> BillingPaymentMethod.create(
                        principal.companyId(),
                        "MASTER",
                        mockLast4(authKey),
                        LocalDate.of(2029, 12, 1),
                        mockBillingKey(authKey, principal.companyId()),
                        true
                ));
        BillingPaymentMethod saved = paymentMethodRepository.save(method);
        return new BillingOverviewResult.PaymentMethodResult(
                saved.getId(),
                saved.getBrand(),
                saved.getLast4(),
                "%02d/%02d".formatted(saved.getExpiresOn().getMonthValue(), saved.getExpiresOn().getYear() % 100)
        );
    }

    @Override
    @Transactional
    public void toggleCancel(AuthPrincipal principal, boolean canceling) {
        requireBillingManager(principal);
        BillingSubscription subscription = findSubscription(principal.companyId());
        if (subscription.getStatus() == BillingSubscriptionStatus.EXPIRED
                || subscription.getStatus() == BillingSubscriptionStatus.UNPAID
                || subscription.getStatus() == BillingSubscriptionStatus.CANCELED) {
            throw new BusinessException(BillingErrorCode.BIL_SUBSCRIPTION_CANCEL_INVALID);
        }
        BillingSubscription updated = canceling
                ? subscription.cancelAtPeriodEnd()
                : subscription.resume();
        subscriptionRepository.save(updated);
    }

    private BillingSubscription findSubscription(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BusinessException(BillingErrorCode.BIL_SUBSCRIPTION_NOT_FOUND));
    }

    private BillingSubscription getOrCreateSubscription(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    LocalDate today = LocalDate.now(clock);
                    return subscriptionRepository.save(BillingSubscription.create(
                            companyId,
                            BillingDefaults.PLAN_CODE,
                            1,
                            BillingSubscriptionStatus.UNPAID,
                            today,
                            today,
                            null,
                            null,
                            0
                    ));
                });
    }

    private void requireBillingManager(AuthPrincipal principal) {
        if (principal == null || principal.companyId() == null
                || (!principal.isAdmin() && !OWNER_ROLE.equals(principal.getAuthority()))) {
            throw new BusinessException(BillingErrorCode.BIL_FORBIDDEN_SCOPE);
        }
    }

    private String mockBillingKey(String authKey, Long companyId) {
        return "mock_billing_" + companyId + "_" + Integer.toUnsignedString(authKey.hashCode());
    }

    private String mockLast4(String authKey) {
        String digits = authKey.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return digits.substring(digits.length() - 4);
        }
        return "%04d".formatted(Math.floorMod(authKey.hashCode(), 10_000));
    }

    private int estimatedAmount(BillingConfigResult config, int carriedOverageAmount) {
        int subtotal = Math.max(0, config.baseFee()) + Math.max(0, carriedOverageAmount);
        int vat = config.isVatIncluded() ? 0 : (int) Math.round(subtotal * VAT_RATE);
        return subtotal + vat;
    }
}
