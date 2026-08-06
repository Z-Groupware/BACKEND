package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.Optional;

/*
 * L4 에 넘길 회의 날짜를 읽는다. "다음 주까지" 같은 상대 표현을 절대 날짜로 바꾸는 기준점이다.
 *
 * 없으면 계층이 상대 표현을 계산하지 않고 dueDate 를 null 로 둔다 — 기준일을 모르는 채
 * 계산하면 **그럴듯하게 틀린 마감**이 보드에 꽂히고, 그건 기한이 없는 것보다 나쁘다.
 * 그래서 이 값을 못 읽었을 때 오늘 날짜로 대체하지 않는다. 분석은 회의가 끝난 뒤 언제든
 * 돌 수 있어서(ANLZ-01 재실행은 몇 주 뒤일 수도 있다) 오늘과 회의 날짜는 다른 값이다.
 *
 * 회의 정보의 주인은 D(회의) 도메인이다. 여기서는 읽기만 한다 —
 * {@link MeetingParticipantProvider} · MeetingAccessPort 와 같은 방식이다.
 */
public interface MeetingDateProvider {

    /*
     * @return 회의가 실제로 열린 날짜. 실제 시작 시각(started_at)이 있으면 그것을, 없으면
     *         예정 시각(start_at)을 쓴다. 회의가 없으면 비어 있다.
     */
    Optional<LocalDate> meetingDateOf(long meetingId);
}
