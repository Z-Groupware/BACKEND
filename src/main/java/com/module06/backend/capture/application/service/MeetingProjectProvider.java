package com.module06.backend.capture.application.service;

import java.util.Optional;

/*
 * 분배할 액션이 달릴 프로젝트를 읽는다. action.project_id 가 NOT NULL 이라 이 값 없이는
 * 액션을 만들 수 없다.
 *
 * 분배 계약(ActionDistributionPort)이 projectId 를 받으므로 A 가 넘겨야 한다. 회의에 이미
 * 프로젝트 태그가 붙어 있고(meeting.project_id · 전 역할 필수) 액션은 그 회의에서 나온 것이니
 * 같은 프로젝트가 맞다 — 여기서 다른 프로젝트를 고를 근거가 없다.
 *
 * 회의 정보의 주인은 D(회의) 도메인이다. 여기서는 읽기만 한다 —
 * {@link MeetingDateProvider} · MeetingAccessPort 와 같은 방식이다.
 */
public interface MeetingProjectProvider {

    /*
     * @return 회의의 프로젝트. 회의가 없으면 비어 있다. **비어 있으면 분배하지 않는다** —
     *         임의의 프로젝트로 채우면 그 액션이 엉뚱한 프로젝트의 보드에 꽂히고,
     *         마감일 기본값도 그 프로젝트에서 계산된다.
     */
    Optional<Long> projectIdOf(long meetingId);
}
