package com.module06.backend.global.security;


public record AuthPrincipal(
        Long memberId,
        Long companyId,
        String role,
        boolean isAdmin,
        Long teamId
) {

    public Long getMemberId() {
        return memberId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getRole() {
        return role;
    }

    public Long getTeamId() {
        return teamId;
    }
}
