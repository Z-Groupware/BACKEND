package com.module06.backend.identity.team.application.command;

/** 역할 생성(§6-10). {@code companyId} 는 토큰에서만 온다 — 요청 본문에 없다. */
public record CreateTeamRoleCommand(
        Long companyId,
        Long teamId,
        String name
) {
}
