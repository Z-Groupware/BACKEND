package com.module06.backend.identity.team.application.command;

/** 역할 이름 수정(§6-11). 부서 이동은 없다 — {@code teamId} 는 소유권 확인용이다. */
public record RenameTeamRoleCommand(
        Long companyId,
        Long teamId,
        Long roleId,
        String name
) {
}
