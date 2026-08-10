package com.module06.backend.capture.presentation.api.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/*
 * ANLZ-02 요청이다.
 *
 * 계층을 enum 으로 받지 않고 문자열로 받는다. 전송 값이 "L1.5"·"L3.5" 라 자바 enum 상수
 * 이름과 다르고(점을 쓸 수 없다), 스프링의 enum 바인딩은 name() 을 본다 — 그대로 두면
 * "L1.5" 가 400 으로 튕기고 사용자는 명세대로 보냈는데 왜 안 되는지 알 수 없다.
 * 변환은 {@code LayerName#fromWireValue} 한 곳에서만 한다.
 */
public record ResumeAnalysisRequest(

        @Schema(description = "재개할 계층. 이 계층부터 다시 돈다", example = "L4")
        @NotBlank(message = "재개할 계층은 필수입니다.")
        String resumeFromLayer
) {
}
