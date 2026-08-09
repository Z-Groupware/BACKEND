package com.module06.backend.reviewloop.judge;

/** LLM Judge가 매기는 finding 신뢰도. 채점엔 안 쓰고, 리뷰·정렬·리포트에 쓴다. */
public enum Confidence {
    LOW,
    MEDIUM,
    HIGH
}
