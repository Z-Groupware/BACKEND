package com.module06.backend.identity.member.application.usecase;

import java.util.List;

import com.module06.backend.identity.member.application.dto.TeamRosterMember;

public interface GetTeamRosterUseCase {

    /**
     * 한 팀의 재직 중인 구성원 이름·id. 회의 참석자 픽커용이라 조회까지가 전부다 — 고른 id 로
     * 회의를 만드는 것은 회의 도메인의 일이다.
     *
     * @param teamId 호출자의 소속 팀. null 이면 빈 목록 — 팀 미배정(온보딩 전 오너)은 오류가 아니다
     */
    List<TeamRosterMember> getTeamRoster(Long companyId, Long teamId);
}
