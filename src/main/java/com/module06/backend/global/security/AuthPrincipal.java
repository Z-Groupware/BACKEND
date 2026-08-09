package com.module06.backend.global.security;


/**
 * {@code authority} 는 화면의 "권한"(OWNER·LEADER·MEMBER)이다. 화면의 "역할"(프론트엔드·백엔드)은
 * 여기 담기지 않는다 — 그것은 인가에 쓰지 않는 라벨이라 토큰에 넣을 이유가 없다.
 */
public record AuthPrincipal(
        Long memberId,
        Long companyId,
        String authority,
        boolean isAdmin,
        Long teamId
) {

    public Long getMemberId() {
        return memberId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getAuthority() {
        return authority;
    }

    public Long getTeamId() {
        return teamId;
    }
}
