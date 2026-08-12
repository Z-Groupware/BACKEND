package com.module06.backend.metering.domain.exception;

import com.module06.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum BillingErrorCode implements ErrorCode {

    BIL_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "BIL-001", "Billing subscription is not configured."),
    BIL_SUBSCRIPTION_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "BIL-002", "Billing subscription request is invalid."),
    BIL_PAYMENT_METHOD_NOT_FOUND(HttpStatus.NOT_FOUND, "BIL-003", "Payment method is not configured."),
    BIL_PAYMENT_METHOD_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "BIL-004", "Payment method request is invalid."),
    BIL_BILLING_CONFIG_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "BIL-005", "Billing config request is invalid."),
    BIL_FORBIDDEN_SCOPE(HttpStatus.FORBIDDEN, "BIL-006", "Billing scope is not allowed."),
    BIL_PAYMENT_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "BIL-007", "Billing history was not found."),
    BIL_PAYMENT_HISTORY_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "BIL-008", "Billing history request is invalid."),
    BIL_PAYMENT_METHOD_COMPANY_MISMATCH(HttpStatus.BAD_REQUEST, "BIL-009", "Payment method customer key is invalid."),
    BIL_SUBSCRIPTION_CANCEL_INVALID(HttpStatus.BAD_REQUEST, "BIL-010", "Billing subscription cannot be canceled or resumed.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
