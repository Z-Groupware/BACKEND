package com.module06.backend.notice.application.event;

/*
 * 공지 저장 트랜잭션 안에서 발행하고 알림 도메인이 커밋 이후 소비하는 내부 이벤트다.
 */
public record NoticeCreatedEvent(
        Long noticeId,
        Long companyId,
        String title
) {
}
