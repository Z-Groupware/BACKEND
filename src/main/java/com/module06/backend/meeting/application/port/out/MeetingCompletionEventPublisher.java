package com.module06.backend.meeting.application.port.out;

import com.module06.backend.meeting.application.event.MeetingCompletionRequestedEvent;

/* MEET-08 완료 이벤트 발행을 Spring 전달 기술과 분리하는 아웃바운드 Port다. */
public interface MeetingCompletionEventPublisher {

    /* 상태 저장 트랜잭션 안에서 완료 이벤트를 발행해 AFTER_COMMIT 소비를 가능하게 한다. */
    void publish(MeetingCompletionRequestedEvent event);
}
