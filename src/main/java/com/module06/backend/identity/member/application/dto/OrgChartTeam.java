package com.module06.backend.identity.member.application.dto;

import java.util.List;

/** 조직도(§7-2). 본부 계층 없이 부서별로 평평하게 나열한다(2026-08-07 결정) — team 끼리 부모-자식이 없다. */
public record OrgChartTeam(
        Long teamId,
        String name,
        List<OrgChartMember> members
) {
}
