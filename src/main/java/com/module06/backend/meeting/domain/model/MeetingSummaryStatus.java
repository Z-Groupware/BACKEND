package com.module06.backend.meeting.domain.model;

/*
 * MEET-04 상세가 노출하는 AI 요약 진행 신호다.
 *
 * 상태 판정은 A 분석 도메인이 소유하며 D는 경계에서 같은 의미의 enum으로 복사만 한다.
 */
public enum MeetingSummaryStatus {

    /* 회의가 아직 종료되지 않아 요약을 시도조차 하지 않은 상태다. */
    NONE,

    /* STT 정본이 아직 완성되지 않아 분석 시작을 기다리는 상태다. */
    WAITING_TRANSCRIPT,

    /* A가 요약을 계산하는 중이다. */
    PROCESSING,

    /* A의 요약과 액션 추출 파이프라인이 정상적으로 끝났다. */
    DONE,

    /* A가 이 회의의 요약 처리가 중단됐다고 판정한 상태다. */
    STALLED,

    /* A의 요약 처리 계층 하나 이상이 실패한 상태다. */
    FAILED
}
