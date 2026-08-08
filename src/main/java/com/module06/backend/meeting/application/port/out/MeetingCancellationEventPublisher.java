package com.module06.backend.meeting.application.port.out;

import com.module06.backend.meeting.application.event.MeetingCanceledEvent;

/* MEET-06 취소 이벤트 발행을 Spring 전달 기술과 분리하는 아웃바운드 Port다. */
public interface MeetingCancellationEventPublisher {

    /* 최초 취소 트랜잭션 안에서 이벤트를 발행해 AFTER_COMMIT 소비를 가능하게 한다. */
    void publish(MeetingCanceledEvent event);
}
