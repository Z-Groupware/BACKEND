package com.module06.backend.meeting.application.port.out;

import com.module06.backend.meeting.application.event.MeetingReservedEvent;
import com.module06.backend.meeting.application.event.MeetingAttendeesAddedEvent;

/*
 * 회의 생명주기 내부 이벤트를 외부 메시지 전달 방식과 분리하는 아웃바운드 포트다.
 */
public interface MeetingEventPublisher {

    /* 회의 예약 이벤트를 애플리케이션 이벤트 채널에 발행한다. */
    void publish(MeetingReservedEvent event);

    /* 참석자 교체로 새로 추가된 구성원 초대 이벤트를 애플리케이션 이벤트 채널에 발행한다. */
    void publish(MeetingAttendeesAddedEvent event);
}
