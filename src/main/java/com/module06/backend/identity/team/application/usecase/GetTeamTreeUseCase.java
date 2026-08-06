package com.module06.backend.identity.team.application.usecase;

import java.util.List;

import com.module06.backend.identity.team.application.dto.TeamNode;

public interface GetTeamTreeUseCase {

    List<TeamNode> getTree(Long companyId);
}
