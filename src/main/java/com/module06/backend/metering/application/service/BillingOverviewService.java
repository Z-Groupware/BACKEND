package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.BillingConfigResult;
import com.module06.backend.metering.application.result.BillingOverviewResult;
import com.module06.backend.metering.application.usecase.GetBillingOverviewUseCase;
import com.module06.backend.metering.domain.exception.BillingErrorCode;
import com.module06.backend.metering.domain.model.BillingPaymentMethod;
import com.module06.backend.metering.domain.model.BillingPaymentRecord;
import com.module06.backend.metering.domain.model.BillingPaymentStatus;
import com.module06.backend.metering.domain.model.BillingSubscription;
import com.module06.backend.metering.domain.model.BillingSubscriptionStatus;
import com.module06.backend.metering.domain.repository.BillingPaymentMethodRepository;
import com.module06.backend.metering.domain.repository.BillingPaymentRecordRepository;
import com.module06.backend.metering.domain.repository.BillingSubscriptionRepository;
import com.module06.backend.metering.domain.repository.BillingUsageQueryRepository;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BillingOverviewService implements GetBillingOverviewUseCase {

    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;
    private static final String PLAN_CODE = "TEAM";
    private static final String PLAN_NAME = "Team";
    private static final double VAT_RATE = 0.10;

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPaymentMethodRepository paymentMethodRepository;
    private final BillingPaymentRecordRepository paymentRecordRepository;
    private final TokenUsageRecordRepository tokenUsageRecordRepository;
    private final BillingUsageQueryRepository billingUsageQueryRepository;
    private final BillingConfigService billingConfigService;
    private final Clock clock;

    public BillingOverviewService(BillingSubscriptionRepository subscriptionRepository,
                                  BillingPaymentMethodRepository paymentMethodRepository,
                                  BillingPaymentRecordRepository paymentRecordRepository,
                                  TokenUsageRecordRepository tokenUsageRecordRepository,
                                  BillingUsageQueryRepository billingUsageQueryRepository,
                                  BillingConfigService billingConfigService,
                                  @Qualifier("meetingClock") Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.tokenUsageRecordRepository = tokenUsageRecordRepository;
        this.billingUsageQueryRepository = billingUsageQueryRepository;
        this.billingConfigService = billingConfigService;
        this.clock = clock;
    }

    @Override
    public BillingOverviewResult getBillingOverview(AuthPrincipal principal) {
        requireAuthenticatedCompany(principal);
        BillingSubscription subscription = subscriptionRepository.findByCompanyId(principal.companyId())
                .orElseThrow(() -> new BusinessException(BillingErrorCode.BIL_SUBSCRIPTION_NOT_FOUND));

        PeriodBounds bounds = bounds(periodOf(subscription));
        long tokens = tokenUsageRecordRepository.sumTotalTokens(principal.companyId(), bounds.start(), bounds.end());
        long recordingBytes = billingUsageQueryRepository.sumRecordingBytes(principal.companyId());
        long sttBytes = billingUsageQueryRepository.sumCaptionAndSummaryBytes(principal.companyId());
        long meetingCount = billingUsageQueryRepository.countMeetings(principal.companyId(), bounds.start(), bounds.end());

        BillingConfigResult config = billingConfigService.getBillingConfig(principal.companyId());
        int estimatedAmount = estimatedAmount(config, subscription.getCarriedOverageAmount());
        BillingSubscriptionStatus status = displayStatus(subscription.getStatus());
        BillingOverviewResult.UsageResult usage = new BillingOverviewResult.UsageResult(
                tokens,
                toGb(recordingBytes),
                toGb(sttBytes),
                meetingCount
        );

        BillingOverviewResult.SubscriptionResult subscriptionResult = new BillingOverviewResult.SubscriptionResult(
                PLAN_CODE,
                PLAN_NAME,
                status,
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                nextBillingDate(status, subscription.getNextBillingDate()),
                subscription.getCarriedOverageAmount(),
                estimatedAmount,
                usage
        );

        return new BillingOverviewResult(
                subscriptionResult,
                paymentMethodRepository.findByCompanyId(principal.companyId())
                        .map(this::toPaymentMethod)
                        .orElse(null),
                paymentRecordRepository.findByCompanyId(principal.companyId()).stream()
                        .filter(record -> record.getStatus() != BillingPaymentStatus.FREE)
                        .map(this::toPaymentRecord)
                        .toList()
        );
    }

    private BillingOverviewResult.PaymentMethodResult toPaymentMethod(BillingPaymentMethod method) {
        return new BillingOverviewResult.PaymentMethodResult(
                method.getId(),
                method.getBrand(),
                method.getLast4(),
                "%02d/%02d".formatted(method.getExpiresOn().getMonthValue(), method.getExpiresOn().getYear() % 100)
        );
    }

    private BillingOverviewResult.PaymentRecordResult toPaymentRecord(BillingPaymentRecord record) {
        return new BillingOverviewResult.PaymentRecordResult(
                record.getId(),
                record.getBilledOn(),
                PLAN_NAME,
                record.getOverageAmount(),
                record.getAmount(),
                record.getStatus()
        );
    }

    private void requireAuthenticatedCompany(AuthPrincipal principal) {
        if (principal == null || principal.companyId() == null) {
            throw new BusinessException(BillingErrorCode.BIL_FORBIDDEN_SCOPE);
        }
    }

    private BillingSubscriptionStatus displayStatus(BillingSubscriptionStatus status) {
        return status == BillingSubscriptionStatus.CANCELED ? BillingSubscriptionStatus.EXPIRED : status;
    }

    private LocalDate nextBillingDate(BillingSubscriptionStatus status, LocalDate nextBillingDate) {
        return status == BillingSubscriptionStatus.UNPAID || status == BillingSubscriptionStatus.EXPIRED
                ? null
                : nextBillingDate;
    }

    private int estimatedAmount(BillingConfigResult config, int carriedOverageAmount) {
        int subtotal = Math.max(0, config.baseFee()) + Math.max(0, carriedOverageAmount);
        int vat = config.isVatIncluded() ? 0 : (int) Math.round(subtotal * VAT_RATE);
        return subtotal + vat;
    }

    private YearMonth periodOf(BillingSubscription subscription) {
        LocalDate currentPeriodStart = subscription.getCurrentPeriodStart();
        if (currentPeriodStart != null) {
            return YearMonth.from(currentPeriodStart);
        }
        return YearMonth.now(clock);
    }

    private PeriodBounds bounds(YearMonth period) {
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();
        return new PeriodBounds(start, end);
    }

    private double toGb(long bytes) {
        return bytes <= 0 ? 0 : (double) bytes / BYTES_PER_GB;
    }

    private record PeriodBounds(LocalDateTime start, LocalDateTime end) {
    }
}
