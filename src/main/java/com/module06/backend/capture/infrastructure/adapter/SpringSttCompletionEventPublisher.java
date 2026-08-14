package com.module06.backend.capture.infrastructure.adapter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.event.SttTranscriptCompletedEvent;
import com.module06.backend.capture.application.port.out.SttCompletionEventPublisher;

/* STT 전체 성공 완료 신호를 스프링 내부 이벤트로 발행한다. */
@Component
@RequiredArgsConstructor
public class SpringSttCompletionEventPublisher implements SttCompletionEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(SttTranscriptCompletedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
