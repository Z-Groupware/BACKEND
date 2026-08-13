package com.module06.backend.capture.infrastructure.adapter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.event.AnalysisCompletedEvent;
import com.module06.backend.capture.application.event.AnalysisFailedEvent;
import com.module06.backend.capture.application.port.out.AnalysisEventPublisher;

/*
 * 분석 애플리케이션 이벤트를 Spring 이벤트 채널에 전달하는 아웃바운드 어댑터다.
 *
 * 호출 시점(MeetingCompletedAnalysisTrigger의 비동기 스레드)에는 대기 중인 트랜잭션이
 * 없다 — 그래서 알림 소비자는 @TransactionalEventListener가 아니라 일반 @EventListener로
 * 구독한다(AnalysisCompletedNotificationTrigger 주석 참조).
 */
@Component
@RequiredArgsConstructor
public class SpringAnalysisEventPublisher implements AnalysisEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /* 분석 완료 이벤트를 Spring 이벤트 채널에 발행한다. */
    @Override
    public void publish(AnalysisCompletedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    /* 분석 실패 이벤트를 Spring 이벤트 채널에 발행한다. */
    @Override
    public void publish(AnalysisFailedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
