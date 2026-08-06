package com.module06.backend.meeting.infrastructure.adapter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meeting.application.event.MeetingCompletionRequestedEvent;
import com.module06.backend.meeting.application.port.out.MeetingCompletionEventPublisher;

/* MEET-08 완료 이벤트를 Spring 애플리케이션 이벤트 채널에 전달하는 어댑터다. */
@Component
@RequiredArgsConstructor
public class SpringMeetingCompletionEventPublisher implements MeetingCompletionEventPublisher {

    /* 프로세스 내부 이벤트를 구독자에게 전달하는 Spring 발행기다. */
    private final ApplicationEventPublisher applicationEventPublisher;

    /* 소비자가 AFTER_COMMIT으로 분석을 시작할 수 있도록 트랜잭션 안에서 이벤트를 발행한다. */
    @Override
    public void publish(MeetingCompletionRequestedEvent event) {
        /* D는 A의 유스케이스를 직접 호출하지 않고 안정된 내부 이벤트 계약만 공개한다. */
        applicationEventPublisher.publishEvent(event);
    }
}
