package com.module06.backend.project.infrastructure.adapter;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.module06.backend.project.application.port.MeetingQueryPort;

/* comment.
    project(C)의 MeetingQueryPort를 meeting(D)의 공개 인바운드 포트로 위임하는 ACL —
    handover(E)의 MeetingQueryPortDelegatingAdapter와 같은 패턴. companyId는 project 쪽에서
    이미 파라미터로 갖고 있어 handover처럼 SecurityContext에서 따로 꺼낼 필요가 없다.

    빈 이름을 명시한다 — handover에도 같은 단순 클래스명의 어댑터가 있어서 기본 이름(단순
    클래스명 첫글자 소문자)이 그대로 충돌한다(ConflictingBeanDefinitionException).
*/
@Component("projectMeetingQueryPortDelegatingAdapter")
public class MeetingQueryPortDelegatingAdapter implements MeetingQueryPort {

    private final com.module06.backend.meeting.application.port.in.MeetingQueryPort meetingQueryPort;

    public MeetingQueryPortDelegatingAdapter(
            com.module06.backend.meeting.application.port.in.MeetingQueryPort meetingQueryPort
    ) {
        this.meetingQueryPort = meetingQueryPort;
    }

    @Override
    public Map<Long, Long> countMeetingsByProjectIds(Long companyId, List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return meetingQueryPort.countMeetingsByProjectIds(companyId, projectIds);
    }
}
