package com.module06.backend.capture.application.service;

import lombok.Getter;

/*
 * 계층 호출 실패다. Python 이 응답 본문에 실어 준 분류를 그대로 들고 온다.
 *
 * **재시도 판정을 메시지 문자열로 추측하지 않는다.** 판정은 실패를 만든 쪽이 한다 —
 * Python 이 `retryable` 을 내려주고(README 「실패 정책」), 이쪽은 그 값을 믿는다.
 * 문자열로 추측하면 영구 실패를 세 번 재시도해 토큰만 태운다.
 *
 * errorCode 는 analysis_layer.error_code 에 그대로 적힌다. 나중에 "이 회의는 무엇으로
 * 실패했나"를 되짚는 유일한 값이라 반드시 채운다.
 */
@Getter
public class AiLayerException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public AiLayerException(String errorCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public AiLayerException(String errorCode, String message, boolean retryable) {
        this(errorCode, message, retryable, null);
    }
}
