package com.module06.backend.identity.member.domain.model;

/** ADMIN 을 넣지 않는다 — 어드민은 role 값이 아니라 member.is_admin 겸직 플래그다(V2.2.3). */
public enum Role {

    OWNER("/owner"),
    LEADER("/team"),
    MEMBER("/my");

    private final String landingPath;

    Role(String landingPath) {
        this.landingPath = landingPath;
    }

    /** 로그인 직후 착지 경로. isAdmin 을 반영하지 않는다 — 어드민 겸직 팀장도 /team 이다. */
    public String landingPath() {
        return landingPath;
    }
}
