package com.module06.backend.meeting.infrastructure.adapter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meeting.application.event.MeetingReservedEvent;
import com.module06.backend.meeting.application.event.MeetingAttendeesAddedEvent;
import com.module06.backend.meeting.application.event.MeetingAttendeesRemovedEvent;
import com.module06.backend.meeting.application.event.MeetingCanceledEvent;
import com.module06.backend.meeting.application.event.MeetingUpdatedEvent;
import com.module06.backend.meeting.application.port.out.MeetingCancellationEventPublisher;
import com.module06.backend.meeting.application.port.out.MeetingEventPublisher;
import com.module06.backend.meeting.application.port.out.MeetingUpdateEventPublisher;

/*
 * 회의 애플리케이션 이벤트를 Spring 이벤트 채널에 전달하는 아웃바운드 어댑터다.
 *
 * 알림 소비자는 @TransactionalEventListener(AFTER_COMMIT)로 구독해야 롤백된 예약 알림을 보내지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SpringMeetingEventPublisher implements
        MeetingEventPublisher,
        MeetingUpdateEventPublisher,
        MeetingCancellationEventPublisher {

    /* 프로세스 내부 이벤트를 전달하는 Spring 발행기다. */
    private final ApplicationEventPublisher applicationEventPublisher;

    /* 예약 완료 이벤트를 Spring 이벤트 채널에 발행한다. */
    @Override
    public void publish(MeetingReservedEvent event) {
        /* 트랜잭션 안에서 발행하고 소비자가 AFTER_COMMIT 단계에서 처리하게 한다. */
        applicationEventPublisher.publishEvent(event);
    }

    /* 새 참석자 초대 이벤트를 Spring 이벤트 채널에 발행한다. */
    @Override
    public void publish(MeetingAttendeesAddedEvent event) {
        /* 트랜잭션 안에서 발행하고 알림 소비자가 AFTER_COMMIT 단계에서 처리하게 한다. */
        applicationEventPublisher.publishEvent(event);
    }

    /* 참석자 제외 이벤트를 Spring 이벤트 채널에 발행한다. */
    @Override
    public void publish(MeetingAttendeesRemovedEvent event) {
        /* 트랜잭션 안에서 발행하고 알림 소비자가 AFTER_COMMIT 단계에서 처리하게 한다. */
        applicationEventPublisher.publishEvent(event);
    }

    /* 회의 정보 수정 이벤트를 Spring 애플리케이션 이벤트 채널에 발행한다. */
    @Override
    public void publish(MeetingUpdatedEvent event) {
        /* 트랜잭션 안에서 발행하고 알림 소비자가 AFTER_COMMIT 단계에서 처리하게 한다. */
        applicationEventPublisher.publishEvent(event);
    }

    /* 최초 회의 취소 이벤트를 Spring 애플리케이션 이벤트 채널에 발행한다. */
    @Override
    public void publish(MeetingCanceledEvent event) {
        /* 트랜잭션 안에서 발행하고 알림 소비자가 AFTER_COMMIT 단계에서 처리하게 한다. */
        applicationEventPublisher.publishEvent(event);
    }
}
