package com.module06.backend.notice.infrastructure.adapter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.module06.backend.notice.application.event.NoticeCreatedEvent;

/*
 * 공지 이벤트 발행 어댑터가 Spring 이벤트 채널에 같은 객체를 전달하는지 검증한다.
 */
@DisplayName("Spring 공지 이벤트 발행 어댑터")
class SpringNoticeEventPublisherTest {

    /* 공지 애플리케이션 이벤트를 변형하지 않고 Spring 발행기에 위임하는지 검증한다. */
    @Test
    @DisplayName("NoticeCreatedEvent를 Spring 이벤트 채널에 발행한다")
    void publishesNoticeCreatedEvent() {
        /* Spring 발행 호출을 검증할 대역과 공지 이벤트를 준비한다. */
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        SpringNoticeEventPublisher publisher = new SpringNoticeEventPublisher(applicationEventPublisher);
        NoticeCreatedEvent event = new NoticeCreatedEvent(31L, 10L, "사내 워크숍 안내");

        /* 공지 Port를 통해 등록 이벤트를 발행한다. */
        publisher.publish(event);

        /* 동일한 이벤트 인스턴스가 Spring 채널에 정확히 한 번 전달돼야 한다. */
        verify(applicationEventPublisher).publishEvent(event);
    }
}
