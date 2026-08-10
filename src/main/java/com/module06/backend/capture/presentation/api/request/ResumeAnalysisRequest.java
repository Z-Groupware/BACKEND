package com.module06.backend.capture.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

/*
 * ANLZ-02 요청이다.
 *
 * 계층을 enum 으로 받지 않고 문자열로 받는다. 전송 값이 "L1.5"·"L3.5" 라 자바 enum 상수
 * 이름과 다르고(점을 쓸 수 없다), 스프링의 enum 바인딩은 name() 을 본다 — 그대로 두면
 * "L1.5" 가 400 으로 튕기고 사용자는 명세대로 보냈는데 왜 안 되는지 알 수 없다.
 * 변환은 {@code LayerName#fromWireValue} 한 곳에서만 한다.
 *
 * <h2>@NotBlank 를 뗐다 — 몸통도 계층도 생략할 수 있다</h2>
 * 화면의 「다시 분석」 버튼은 어느 계층이 실패했는지 모른다. 필수로 두면 호출자가 CAP-06 을
 * 먼저 불러 failedLayer 를 얻는 왕복이 생기고, 그걸 피하려고 ANLZ-01(force)로 돌리면 이미
 * 성공한 계층의 토큰이 다시 나간다 — 재개 API 가 막으려던 바로 그것이다.
 *
 * 그래서 **생략하면 서버가 고른다**(처음 깨진 계층). 검증을 푼 것이 아니라 판단하는 자리를
 * 옮긴 것이고, 빈 문자열은 여전히 400 이다 — 계층을 보내려다 실패한 요청과 아예 안 보낸
 * 요청은 뜻이 다르다({@code ResumeAnalysisUseCase#resume} 주석).
 */
public record ResumeAnalysisRequest(

        @Schema(description = "재개할 계층. 생략하면 처음 깨진 계층에서 자동 재개한다", example = "L4")
        String resumeFromLayer
) {

    /* 몸통 자체가 없는 요청(계층 미지정)을 null 로 접는다 — 컨트롤러가 분기하지 않게 한다. */
    public static String layerOf(ResumeAnalysisRequest request) {
        return request == null ? null : request.resumeFromLayer();
    }
}
