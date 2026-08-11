package com.module06.backend.notice.application.port.out;

import com.module06.backend.notice.application.event.NoticeCreatedEvent;

/*
 * 공지 애플리케이션이 외부 이벤트 전달 기술을 알지 않고 등록 완료 사실을 발행하는 아웃바운드 포트다.
 */
public interface NoticeEventPublisher {

    /* 저장된 공지 식별자·회사·제목을 가진 등록 이벤트를 애플리케이션 이벤트 채널에 발행한다. */
    void publish(NoticeCreatedEvent event);
}
