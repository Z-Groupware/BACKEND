package com.module06.backend.capture.application.service;

import java.time.Duration;
import java.util.Optional;

/*
 * 회의가 실제로 몇 분이었는지 읽는다. **자동 분석의 비용 관문**이 이 값을 본다.
 *
 * 예정 시간(start_at ~ end_at)이 아니라 실측(started_at ~ ended_at)이다. 30분으로 잡아두고
 * 2분 만에 끝난 회의가 예정으로는 30분이라, 예정을 보면 관문이 아무것도 거르지 못한다.
 *
 * 회의 정보의 주인은 D(회의) 도메인이다. 여기서는 읽기만 한다 —
 * {@link MeetingDateProvider} · {@link MeetingParticipantProvider} 와 같은 방식이다.
 */
public interface MeetingLengthProvider {

    /*
     * @return 실제 시작~종료 길이. 회의가 없거나 두 시각 중 하나라도 비어 있으면 **비어 있다.**
     *         0 이나 임의값으로 채우지 않는다 — 모르는 것과 짧은 것은 다르고, 모르는 값을
     *         짧다고 읽으면 멀쩡한 회의의 분석이 조용히 건너뛰어진다.
     */
    Optional<Duration> actualLengthOf(long meetingId);

    default Optional<Boolean> isOnline(long meetingId) {
        return Optional.empty();
    }
}
