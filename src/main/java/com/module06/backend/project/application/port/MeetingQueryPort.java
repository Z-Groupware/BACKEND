package com.module06.backend.project.application.port;

import java.util.List;
import java.util.Map;

/* comment.
    project(C)가 선언하고, meeting(D) 도메인의 delegating adapter가 구현하는 아웃바운드 포트.
    project는 meeting 엔티티·Repository를 직접 참조하지 않고 이 계약으로만 회의 수를 조회한다
    (0절 절대규칙 1항, handover의 MeetingQueryPortDelegatingAdapter와 같은 패턴).
*/
public interface MeetingQueryPort {

    // 프로젝트 목록 meetingCount 표시용 — 취소 회의는 제외, 회의가 없는 프로젝트도 0으로 채워 온다
    // (D 계약, 2026-08-09 성진 확정). projectIds가 비어있으면 호출하지 않는다.
    Map<Long, Long> countMeetingsByProjectIds(Long companyId, List<Long> projectIds);
}
