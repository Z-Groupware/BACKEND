package com.module06.backend.metering.domain.exception;

import com.module06.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MeteringErrorCode implements ErrorCode {

    MT_RECORD_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "MT-001", "Token usage record request is invalid."),
    MT_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "MT-002", "Company token plan is not configured."),
    MT_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "MT-003", "Metering period must be formatted as YYYY-MM."),
    MT_FORBIDDEN_SCOPE(HttpStatus.FORBIDDEN, "MT-004", "Metering scope is not allowed."),
    MT_PLAN_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "MT-005", "Company token plan request is invalid."),
    MT_STORAGE_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "MT-006", "Company storage plan is not configured."),
    MT_STORAGE_PLAN_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "MT-007", "Company storage plan request is invalid."),
    MT_STORAGE_RECORD_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "MT-008", "Storage usage report request is invalid.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
