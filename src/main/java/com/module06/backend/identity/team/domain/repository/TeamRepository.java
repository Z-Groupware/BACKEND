package com.module06.backend.identity.team.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.identity.team.domain.model.Team;

public interface TeamRepository {

    List<Team> findByCompanyId(Long companyId);

    Optional<Team> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * 이 구성원이 팀장으로 앉아 있는 부서. 오프보딩이 팀장 자리를 비울 때 쓴다 — 회사 스코프를
     * 받지 않아도 되는 이유는 {@code UK_TEAM_LEADER_MEMBER}(V2.2.6)가 한 사람이 두 부서의
     * 팀장이 되는 것을 막고 있기 때문이다.
     */
    Optional<Team> findByLeaderMemberId(Long leaderMemberId);

    Team create(Long companyId, String name);

    void rename(Long id, String name);

    /**
     * 팀장 교체. 구성원 상세의 역할 변경(§7-4)이 흡수한 부수효과 전용이다 — 별도 팀장 지정
     * 엔드포인트는 폐기됐다(§6-5). {@code leaderMemberId} 가 null 이면 팀장 미지정 상태가 된다.
     */
    void updateLeader(Long id, Long leaderMemberId);

    void delete(Long id);

    boolean existsByCompanyIdAndName(Long companyId, String name);
}
