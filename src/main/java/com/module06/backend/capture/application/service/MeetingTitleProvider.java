package com.module06.backend.capture.application.service;

import java.util.Optional;

/*
 * 완료·실패 알림 문구에 넣을 회의 제목을 읽는다.
 *
 * 회의 정보의 주인은 D(회의) 도메인이다. 여기서는 읽기만 한다 —
 * {@link MeetingDateProvider} · {@link MeetingHostProvider} 와 같은 방식이다.
 */
public interface MeetingTitleProvider {

    /*
     * @return 회의 제목. 회의가 없으면 비어 있다.
     */
    Optional<String> titleOf(long meetingId);
}
