package com.module06.backend.notice.infrastructure.adapter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.notice.application.event.NoticeCreatedEvent;
import com.module06.backend.notice.application.port.out.NoticeEventPublisher;

/*
 * 공지 애플리케이션 이벤트를 Spring 프로세스 내부 이벤트 채널로 전달하는 아웃바운드 어댑터다.
 */
@Component
@RequiredArgsConstructor
public class SpringNoticeEventPublisher implements NoticeEventPublisher {

    /* AFTER_COMMIT 리스너에 공지 등록 이벤트를 전달하는 Spring 발행기다. */
    private final ApplicationEventPublisher applicationEventPublisher;

    /* 공지 등록 트랜잭션 안에서 이벤트를 발행하고 실제 소비는 커밋 이후로 미룬다. */
    @Override
    public void publish(NoticeCreatedEvent event) {
        /* Spring 이벤트 채널에 전달해 공지 서비스와 notification 구현의 직접 결합을 막는다. */
        applicationEventPublisher.publishEvent(event);
    }
}
