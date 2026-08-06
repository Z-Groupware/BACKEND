package com.module06.backend.identity.member.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Authority")
class RoleTest {

    @ParameterizedTest
    @CsvSource({
            "OWNER,  /owner",
            "LEADER, /team",
            "MEMBER, /my"
    })
    @DisplayName("역할별 착지 경로 — 명세 ROLE_LANDING 과 글자까지 같아야 프론트가 응답을 그대로 쓴다")
    void mapsRoleToLandingPath(Authority role, String expected) {
        assertThat(role.landingPath()).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(Authority.class)
    @DisplayName("모든 역할이 착지 경로를 가진다 — 역할을 추가하고 매핑을 잊으면 로그인 직후 갈 곳이 없다")
    void everyRoleHasLandingPath(Authority role) {
        assertThat(role.landingPath()).startsWith("/");
    }
}
