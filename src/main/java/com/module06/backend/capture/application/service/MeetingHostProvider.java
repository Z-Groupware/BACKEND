package com.module06.backend.capture.application.service;

import java.util.Optional;

/*
 * 회의 담당자(host)를 읽는다. RVW-05 가 **누가 분배를 확정할 수 있는가**를 이 값으로 가른다.
 *
 * 왜 담당자만인가 — 분배는 되돌리기 어렵다. 액션이 사람들 보드에 꽂히고 나면 회수 경로가 없고,
 * 참석자 아무나 누를 수 있으면 검토가 끝나지 않은 회의가 먼저 나가버린다. 검토(RVW-02)는
 * 참석자 누구나 하되 **마지막 버튼은 한 사람**이라는 뜻이다.
 *
 * 회의 정보의 주인은 D(회의) 도메인이다. 여기서는 읽기만 한다 —
 * {@link MeetingDateProvider} · {@link MeetingProjectProvider} 와 같은 방식이다.
 */
public interface MeetingHostProvider {

    /*
     * @return 회의 담당자의 member id. 회의가 없으면 비어 있다. **비어 있으면 확정하지 않는다** —
     *         담당자를 모르는 채 통과시키면 그 검사는 없는 것과 같다.
     */
    Optional<Long> hostMemberIdOf(long meetingId);
}
