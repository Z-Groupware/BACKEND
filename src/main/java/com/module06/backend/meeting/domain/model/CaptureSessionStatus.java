package com.module06.backend.meeting.domain.model;

/*
 * D 도메인이 소유하는 캡처 세션의 생명주기 상태다.
 *
 * 현재 녹음자와 청크 처리 상태는 A 도메인이 소유하므로 이 상태에는 포함하지 않는다.
 */
public enum CaptureSessionStatus {

    /* 캡처 세션이 시작되어 일시정지되지 않은 상태다. */
    ACTIVE,

    /* host가 캡처 세션을 일시정지한 상태다. */
    PAUSED,

    /* 회의 종료와 함께 캡처 세션이 종료된 상태다. */
    ENDED
}
