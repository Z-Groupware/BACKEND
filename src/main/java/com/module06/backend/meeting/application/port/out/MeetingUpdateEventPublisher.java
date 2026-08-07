package com.module06.backend.meeting.application.port.out;

import com.module06.backend.meeting.application.event.MeetingUpdatedEvent;

/*
 * MEET-05 수정 이벤트를 실제 메시지 전달 기술과 분리하는 아웃바운드 Port다.
 */
public interface MeetingUpdateEventPublisher {

    /* 커밋 후 알림 소비자가 처리할 회의 정보 수정 이벤트를 발행한다. */
    void publish(MeetingUpdatedEvent event);
}
