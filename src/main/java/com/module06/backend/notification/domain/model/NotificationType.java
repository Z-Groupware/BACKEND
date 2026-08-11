package com.module06.backend.notification.domain.model;

/*
 * 알림 저장값과 SSE payload에서 공통으로 사용하는 이벤트 종류다.
 * 외부 계약에는 enum 이름을 문자열로 전달해 오타로 서로 다른 타입이 생기지 않게 한다.
 */
public enum NotificationType {

    /* 회의가 정상적으로 개설된 뒤 회사 구성원에게 보내는 알림이다. */
    MEETING_CREATED,

    /* 예정된 회의의 시작 10분 전에 보내는 알림이다. */
    MEETING_REMINDER,

    /* 예정된 회의가 취소된 뒤 참석자에게 보내는 알림이다. */
    MEETING_CANCELED,

    /* 공지 등록 커밋 뒤 회사 구성원에게 저장 없이 실시간으로 보내는 배너 이벤트다. */
    NOTICE_CREATED
}
