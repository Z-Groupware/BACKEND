package com.module06.backend.capture.application.service;

import java.util.OptionalInt;

/*
 * 회의의 참석자 수를 읽는다. **STT 화자 분리의 상한(MaxSpeakerLabels)이 이 값이다.**
 *
 * <h2>왜 상한을 주는가 — 넘치면 닻을 못 내린다</h2>
 * Transcribe 는 목소리를 군집화해 라벨을 붙이고, 상한을 넉넉히 주면 같은 사람을 둘로
 * 쪼개는 쪽으로 틀린다(마이크 거리가 바뀌거나 목소리가 커졌다 작아지면 그렇다).
 * 3명 회의에서 라벨이 8개 나오면 라벨→사람 판정이 그만큼 어려워진다 — 앵커는 라벨마다
 * 따로 필요한데 근거는 회의 하나 분량 그대로이기 때문이다.
 *
 * 반대로 부족하게 주면 **두 사람이 한 라벨로 합쳐진다.** 그쪽이 더 나쁘다 — 합쳐진 라벨에
 * 닻을 내리면 남의 발화가 확정으로 그 사람 것이 되고, 그건 판정을 포기하는 것보다 나쁜
 * 실패다(SpeakerAttributionResolver 클래스 주석의 규칙). 그래서 명단 그대로를 준다.
 *
 * <h2>못 읽으면 비어 있다 — 지어내지 않는다</h2>
 * 호출자가 그때 무엇을 할지 정한다. 회의 정보의 주인은 D(회의) 도메인이고 여기서는 읽기만
 * 한다 — {@link MeetingDateProvider} · MeetingParticipantProvider 와 같은 방식이다.
 */
public interface MeetingAttendeeCountProvider {

    /*
     * @return meeting_attendee 의 그 회의 행 수. 회의가 없거나 명단이 비었으면 비어 있다
     *         (0 을 돌려주지 않는다 — "명단이 없다"와 "0명"은 부를 곳이 없다는 점에서 같고,
     *         둘을 나눠 봐야 호출자가 할 수 있는 일이 다르지 않다)
     */
    OptionalInt attendeeCountOf(long meetingId);
}
