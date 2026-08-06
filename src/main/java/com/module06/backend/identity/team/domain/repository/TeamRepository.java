package com.module06.backend.identity.team.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.identity.team.domain.model.Team;

public interface TeamRepository {

    List<Team> findByCompanyId(Long companyId);

    Optional<Team> findByIdAndCompanyId(Long id, Long companyId);

    Team create(Long companyId, Long parentTeamId, String name);

    void rename(Long id, String name);

    void delete(Long id);

    boolean existsByCompanyIdAndParentTeamIdAndName(Long companyId, Long parentTeamId, String name);

    boolean existsByCompanyIdAndParentTeamIdIsNullAndName(Long companyId, String name);

    boolean existsByParentTeamId(Long parentTeamId);
}
