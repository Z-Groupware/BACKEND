package com.module06.backend.global.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        String errorCode,
        String message,
        LocalDateTime timestamp,
        String path,
        String traceId,
        List<FieldErrorDetail> details
) {

    public record FieldErrorDetail(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, String traceId) {
        return of(errorCode, path, traceId, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, String traceId, List<FieldErrorDetail> details) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), LocalDateTime.now(), path, traceId, details);
    }
}
