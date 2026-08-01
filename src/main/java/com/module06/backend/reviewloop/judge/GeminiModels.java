package com.module06.backend.reviewloop.judge;

/**
 * Gemini 모델 지정 — 어댑터 공통(단일 진실 공급원).
 *
 * 구체 버전으로 고정한다: "-latest" 별칭은 Google이 실제 모델을 교체하는 순간
 * 우리 코드가 그대로인데도 판정·생성 결과가 바뀌어(재현 불가) 게이트를 신뢰할 수 없게 만든다.
 * 어댑터마다 따로 두면 모델 교체 시 일부만 고쳐져 어댑터끼리 서로 다른 모델을 쓰게 되므로 여기 한 곳에 둔다.
 *
 * 모델 교체는 GEMINI_MODEL 환경변수로 명시적으로만 한다.
 */
final class GeminiModels {

    /** 고정 모델 — 별칭이 아닌 구체 버전. */
    static final String PINNED = "gemini-3.5-flash";

    /** 실제 사용 모델 — GEMINI_MODEL이 있으면 그것, 없으면 고정 모델. */
    static String resolve() {
        return System.getenv().getOrDefault("GEMINI_MODEL", PINNED);
    }

    private GeminiModels() {
    }
}
