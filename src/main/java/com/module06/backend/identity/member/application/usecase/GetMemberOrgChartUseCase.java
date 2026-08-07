package com.module06.backend.identity.member.application.usecase;

import java.util.List;

import com.module06.backend.identity.member.application.dto.OrgChartTeam;

public interface GetMemberOrgChartUseCase {

    List<OrgChartTeam> getOrgChart(Long companyId);
}
