package com.module06.backend.meeting.domain.model;

/*
 * MEET-17 대시보드 최근 회의 카드가 구분하는 조회 범위다.
 *
 * 역할만으로 범위를 추론하지 않고 요청자가 scope를 명시적으로 지정한다 —
 * Owner도 자신이 참석자로만 등록된 회의를 me로 조회할 수 있어야 하기 때문이다.
 */
public enum DashboardMeetingScope {

    /* host_member_id가 요청자 본인인 회의다. OWNER 역할만 조회할 수 있다. */
    OWNER,

    /* team_id가 요청자 팀과 같고 related_action_id가 있는 회의다. LEADER 역할만 조회할 수 있다. */
    TEAM,

    /* 요청자가 참석자로 등록된 회의다. 모든 인증 역할이 조회할 수 있다. */
    ME
}
