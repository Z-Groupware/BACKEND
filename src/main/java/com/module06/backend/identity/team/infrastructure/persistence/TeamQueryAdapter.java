package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meeting.application.port.out.TeamQueryPort;

import lombok.RequiredArgsConstructor;

/**
 * 회의 대시보드가 팀 배치를 얻는 창구 — {@link TeamQueryPort} 의 구현이다(MEET-17).
 *
 * <p>새로 계산하는 값은 없다. {@code team.leader_member_id} 를 그대로 노출할 뿐이고,
 * "개설자가 팀장인가" 판정은 호출자(회의 도메인) 몫이다 — 조직 도메인이 표시 문구를 정하면
 * 카드 문구가 바뀔 때마다 이 어댑터를 고쳐야 한다.
 *
 * <p>회사 경계는 여기서 자른다. 다른 회사·존재하지 않는 팀 id 는 조용히 빠진다 —
 * 카드 조립은 teamId 기준 재매핑이라 없는 항목이 있어도 나머지는 그려져야 한다.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamQueryAdapter implements TeamQueryPort {

    private final SpringDataTeamRepository teamRepository;

    @Override
    public List<TeamSnapshot> findTeams(Long companyId, List<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        return teamRepository.findByCompanyIdAndIdIn(companyId, teamIds).stream()
                .map(team -> new TeamSnapshot(team.getId(), team.getName(), team.getLeaderMemberId()))
                .toList();
    }
}
