package com.module06.backend.identity.member.domain.model;

/**
 * 화면의 "권한"이다 — 화면의 "역할"은 이것이 아니라 {@code role} 테이블(구 sub_team)이다.
 *
 * <p>이름이 {@code Role} 이 아닌 이유가 여기 있다. 사원에게 붙는 라벨(프론트엔드·백엔드)을 화면이
 * "역할"이라고 부르므로, 그쪽에 {@code role} 을 내주고 인가 축은 {@code authority} 로 부른다.
 *
 * <p>ADMIN 을 넣지 않는다 — 어드민은 값이 아니라 {@code member.is_admin} 겸직 플래그다(V2.2.3).
 */
public enum Authority {

    OWNER("/owner"),
    LEADER("/team"),
    MEMBER("/my");

    private final String landingPath;

    Authority(String landingPath) {
        this.landingPath = landingPath;
    }

    /** 로그인 직후 착지 경로. isAdmin 을 반영하지 않는다 — 어드민 겸직 팀장도 /team 이다. */
    public String landingPath() {
        return landingPath;
    }
}
