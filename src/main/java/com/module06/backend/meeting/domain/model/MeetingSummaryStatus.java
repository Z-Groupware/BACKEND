package com.module06.backend.meeting.domain.model;

/*
 * MEET-04 상세가 노출하는 AI 요약 진행 신호다. MEET-08 응답의 processingStatus와
 * 같은 어휘를 쓰도록 4상태로 설계했다.
 *
 * A가 아직 PROCESSING·DONE을 구분해 주지 않아 지금은 NONE·STALLED만 채운다.
 * 회의가 종료됐고 중단도 아니면 실제로 PROCESSING인지 DONE인지 알 수 없으므로,
 * MeetingDetailResult.summaryStatus()는 그 경우 이 enum 대신 null을 반환한다 —
 * 모르는 값을 이 둘 중 하나로 추측해 내려주지 않는다. A가 구분값을 주면 그때
 * PROCESSING·DONE 분기를 채워 null을 없앤다.
 */
public enum MeetingSummaryStatus {

    /* 회의가 아직 종료되지 않아 요약을 시도조차 하지 않은 상태다. */
    NONE,

    /* A가 요약을 계산하는 중이다 — 아직 A가 이 상태를 구분해 주지 않아 미사용이다. */
    PROCESSING,

    /* A의 요약이 정상적으로 끝났다 — 아직 A가 이 상태를 구분해 주지 않아 미사용이다. */
    DONE,

    /* A가 이 회의의 요약이 중단되거나 실패했다고 보고한 상태다. */
    STALLED
}
