package com.module06.backend.metering.domain.model;

/**
 * v0 assumption, FE mock parity.
 */
public final class BillingDefaults {

    public static final String PLAN_CODE = "STANDARD";
    public static final int BASE_FEE = 150_000;
    public static final long INCLUDED_TOKENS = 1_500_000L;
    public static final long INCLUDED_STORAGE_GB = 50L;
    public static final int OVERAGE_PER_THOUSAND_TOKENS = 20;
    public static final int OVERAGE_PER_GB_MONTH = 500;
    public static final boolean VAT_INCLUDED = false;

    private BillingDefaults() {
    }
}
