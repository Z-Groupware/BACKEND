package com.module06.backend.identity.member.application.dto;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.Authority;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MyProfile")
class MyProfileTest {

    @Test
    @DisplayName("착지 경로를 역할에서 뽑는다 — 저장된 값이 아니라 파생값이라 역할이 바뀌면 같이 바뀐다")
    void derivesLandingPathFromRole() {
        assertThat(profileWith(Authority.LEADER, true).landingPath()).isEqualTo("/team");
    }

    @Test
    @DisplayName("어드민을 겸직해도 착지 경로는 역할 그대로다 — 팀장 겸 어드민은 /manage 가 아니라 /team")
    void adminDoesNotChangeLandingPath() {
        MyProfile plainLeader = profileWith(Authority.LEADER, false);
        MyProfile adminLeader = profileWith(Authority.LEADER, true);

        assertThat(adminLeader.landingPath()).isEqualTo(plainLeader.landingPath());
    }

    @Test
    @DisplayName("온보딩 전 오너는 팀·하위팀·직급이 전부 null 이어도 만들어진다 — 가드가 필수를 가정하면 오너가 로그인 직후 터진다")
    void ownerBeforeOnboardingHasNoTeamOrPosition() {
        MyProfile owner = new MyProfile(
                1L, 1L, "(주)테크스타트", "8AS2-G8T1",
                "홍길동", "owner@techstart.co.kr", "010-0000-0000",
                null, null, null, null, null,
                Authority.OWNER, false, false,
                MemberStatus.ACTIVE, LocalDate.of(2026, 8, 5), "FREE");

        assertThat(owner.teamId()).isNull();
        assertThat(owner.positionId()).isNull();
        assertThat(owner.landingPath()).isEqualTo("/owner");
    }

    private MyProfile profileWith(Authority role, boolean isAdmin) {
        return new MyProfile(
                3L, 1L, "(주)테크스타트", "8AS2-G8T1",
                "이하윤", "hayun@zgroup.co.kr", "010-1000-0003",
                2L, "개발팀", "프론트엔드", 4L, "선임",
                role, isAdmin, true,
                MemberStatus.ACTIVE, LocalDate.of(2022, 5, 10), "FREE");
    }
}
