package com.module06.backend.capture.domain.model;

/*
 * 주제 안에서 오간 항목의 종류다(meeting_decision.item_type).
 *
 * DISCUSSION 을 버리지 않고 남기는 이유는 V5.8 주석과 같다 — "이 안건은 담당자가 안 정해졌다"고
 * 짚어주는 것 자체가 결과물이고, 나중에 L3.5 게이트를 조일지 풀지 판단할 근거가 된다.
 */
public enum ItemType {

    /* 무엇을 하기로 실제로 정해졌다. L3.5 게이트를 통과하면 L4 로 넘어간다. */
    DECISION,

    /* 이야기는 됐지만 정해지지 않았다. */
    DISCUSSION,

    /* 진행을 막고 있는 문제·의존. */
    BLOCKER
}
