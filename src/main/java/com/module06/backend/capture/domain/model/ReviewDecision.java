package com.module06.backend.capture.domain.model;

/*
 * 사람이 검토 화면에서 내리는 판정이다(RVW-02).
 *
 * <h2>CONFIRM 도 반드시 남는다</h2>
 * 수정·반려만 기록하면 라벨셋에 **오답 사례만 쌓여 분포가 왜곡된다.** AI 가 맞힌 것도 똑같이
 * 정답 라벨이고, 그게 없으면 "정확도 80%"라는 숫자 자체를 만들 수 없다 — 틀린 것만 세면
 * 분모가 없다(V5.9 주석 · 명세 RVW-02 처리 정책).
 */
public enum ReviewDecision {

    /* 무수정 승인. AI 가 낸 값이 그대로 정답이다. */
    CONFIRM,

    /* 담당자·기한을 고쳤다. 고친 값이 정답이고 AI 값은 오답 사례로 남는다. */
    MODIFY,

    /* 이 액션 자체가 잘못됐다. 보드로 가지 않는다. */
    REJECT
}
