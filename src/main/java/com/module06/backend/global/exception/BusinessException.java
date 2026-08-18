package com.module06.backend.global.exception;

import java.util.List;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ErrorResponse.FieldErrorDetail> details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    /*
     * 막힌 사유를 사유별 건수로 함께 실어야 할 때 쓴다(RVW-05 확정 거절 등). 대부분의
     * BusinessException은 details가 필요 없어(원인이 코드 하나로 충분히 설명된다) 기존
     * 두 생성자는 그대로 두고 이 경로만 추가한다.
     */
    public BusinessException(ErrorCode errorCode, List<ErrorResponse.FieldErrorDetail> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }
}
