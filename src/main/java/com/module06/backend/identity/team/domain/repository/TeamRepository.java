package com.module06.backend.identity.team.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.identity.team.domain.model.Team;

public interface TeamRepository {

    List<Team> findByCompanyId(Long companyId);

    Optional<Team> findByIdAndCompanyId(Long id, Long companyId);

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
