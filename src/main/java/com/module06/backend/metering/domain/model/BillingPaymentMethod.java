package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.BillingErrorCode;

import java.time.LocalDate;
import java.util.Objects;

public class BillingPaymentMethod {

    private final Long id;
    private final Long companyId;
    private final String brand;
    private final String last4;
    private final LocalDate expiresOn;
    // KNOWN GAP - plaintext OK only because PG is mocked; when Toss is wired, billing_key MUST be
    // envelope-encrypted (KMS) before persist and decrypted only at PG call.
    private final String billingKey;
    private final boolean defaultMethod;

    private BillingPaymentMethod(Long id, Long companyId, String brand, String last4, LocalDate expiresOn,
                                 String billingKey, boolean defaultMethod) {
        this.id = id;
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.brand = requireText(brand, "brand");
        this.last4 = requireLast4(last4);
        this.expiresOn = Objects.requireNonNull(expiresOn, "expiresOn must not be null");
        this.billingKey = billingKey;
        this.defaultMethod = defaultMethod;
    }

    public static BillingPaymentMethod create(Long companyId, String brand, String last4, LocalDate expiresOn,
                                              String billingKey, boolean defaultMethod) {
        requireText(billingKey, "billingKey");
        return new BillingPaymentMethod(null, companyId, brand, last4, expiresOn, billingKey, defaultMethod);
    }

    public static BillingPaymentMethod restore(Long id, Long companyId, String brand, String last4,
                                               LocalDate expiresOn, String billingKey, boolean defaultMethod) {
        return new BillingPaymentMethod(id, companyId, brand, last4, expiresOn, billingKey, defaultMethod);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(BillingErrorCode.BIL_PAYMENT_METHOD_COMMAND_INVALID);
        }
        return value;
    }

    private static String requireLast4(String value) {
        if (value == null || value.length() != 4) {
            throw new BusinessException(BillingErrorCode.BIL_PAYMENT_METHOD_COMMAND_INVALID);
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getBrand() {
        return brand;
    }

    public String getLast4() {
        return last4;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public String getBillingKey() {
        return billingKey;
    }

    public boolean isDefaultMethod() {
        return defaultMethod;
    }
}
