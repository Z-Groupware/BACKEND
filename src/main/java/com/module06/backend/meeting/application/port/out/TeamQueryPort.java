package com.module06.backend.meeting.application.port.out;

import java.util.List;

/*
 * MEET-17 대시보드 카드가 팀 배치를 일괄 조회하는 아웃바운드 포트다.
 *
 * 팀 이름과 팀장의 원본은 조직 도메인이므로 회의 도메인은 값을 저장하거나 캐시하지 않는다.
 * 개설자가 그 팀의 팀장인지("(팀장)" 표기)는 호출자가 leaderMemberId 와 개설자 id 를 비교해 정한다 —
 * 조직 도메인은 team.leader_member_id 를 그대로 노출만 한다.
 */
public interface TeamQueryPort {

    /*
     * 요청 회사에 속한 팀을 식별자 목록으로 한 번에 조회한다.
     *
     * 다른 회사·존재하지 않는 팀 id 는 조용히 결과에서 빠진다 — 카드 조립은 teamId 기준 재매핑이라
     * 없는 항목이 있어도 나머지 카드는 그려져야 한다. 순서는 보장하지 않는다.
     */
    List<TeamSnapshot> findTeams(Long companyId, List<Long> teamIds);

    /* 팀장 미지정이면 leaderMemberId 가 null 이다(V2.2.6) — "팀장 없음"이 정상 경로다. */
    record TeamSnapshot(
            Long teamId,
            String teamName,
            Long leaderMemberId
    ) {
    }
}
