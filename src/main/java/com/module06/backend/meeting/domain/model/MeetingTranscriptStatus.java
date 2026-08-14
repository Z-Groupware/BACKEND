package com.module06.backend.meeting.domain.model;

/*
 * MEET-04 발화 기록 영역이 AI 요약 상태와 독립적으로 사용하는 STT 정본 상태다.
 *
 * 실제 블록 판정은 데이터를 소유한 A 도메인이 수행하고 D는 응답 계약용 값만 보유한다.
 */
public enum MeetingTranscriptStatus {

    /* STT 블록이 없어 받아쓰기가 시작되지 않은 상태다. */
    NOT_STARTED,

    /* STT 블록 처리 또는 정본 적재가 진행 중인 상태다. */
    PROCESSING,

    /* 모든 STT 블록의 정본 적재가 끝난 상태다. */
    DONE,

    /* STT 블록 하나 이상이 실패해 정본이 완성되지 않은 상태다. */
    FAILED
}
