package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.application.dto.MyProfile;
import com.module06.backend.identity.member.application.port.out.MyProfileQueryPort;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.Plan;
import com.module06.backend.identity.member.domain.model.Role;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * /me 는 프론트가 모든 화면 진입에서 부르는 부트스트랩이라, 필드 하나가 비면 사이드바 분기가 깨진다.
 * 그래서 매핑을 전부 확인한다.
 */
@DisplayName("MyProfileQueryAdapter")
@SpringBootTest
@Transactional
class MyProfileQueryAdapterTest {

    @Autowired
    private MyProfileQueryPort port;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("회원·회사·팀·하위팀·직급·구독을 한 번에 채운다")
    void fillsEveryFieldFromJoinedTables() {
        insertCompany(1L, "8AS2-G8T1", "(주)테크스타트", "2026-08-01 10:00:00");
        insertTeam(2L, "개발팀");
        insertSubTeam(5L, "프론트엔드");
        insertJobPosition(4L, "선임");
        insertSubscription(7L, 1L, "TEAM", "ACTIVE");
        insertMember(3L, 1L, 2L, 5L, 4L, "hayun@zgroup.co.kr", "이하윤",
                "010-1000-0003", "MEMBER", false, "ACTIVE", "2022-05-10", null);

        MyProfile profile = port.findByMemberId(3L).orElseThrow();

        assertThat(profile.memberId()).isEqualTo(3L);
        assertThat(profile.companyId()).isEqualTo(1L);
        assertThat(profile.companyName()).isEqualTo("(주)테크스타트");
        assertThat(profile.companyCode()).isEqualTo("8AS2-G8T1");
        assertThat(profile.name()).isEqualTo("이하윤");
        assertThat(profile.email()).isEqualTo("hayun@zgroup.co.kr");
        assertThat(profile.phone()).isEqualTo("010-1000-0003");
        assertThat(profile.teamId()).isEqualTo(2L);
        assertThat(profile.teamName()).isEqualTo("개발팀");
        assertThat(profile.roleLabel()).isEqualTo("프론트엔드");
        assertThat(profile.jobPositionId()).isEqualTo(4L);
        assertThat(profile.positionName()).isEqualTo("선임");
        assertThat(profile.role()).isEqualTo(Role.MEMBER);
        assertThat(profile.isAdmin()).isFalse();
        assertThat(profile.isOnboarded()).isTrue();
        assertThat(profile.workStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(profile.joinedOn()).isEqualTo("2022-05-10");
        assertThat(profile.plan()).isEqualTo(Plan.TEAM);
        assertThat(profile.landingPath()).isEqualTo("/my");
    }

    @Test
    @DisplayName("온보딩 전 오너 — 팀·하위팀·직급이 null 이고 isOnboarded 가 false 다")
    void ownerBeforeOnboarding() {
        insertCompany(11L, "Q53Y-R9HD", "(주)신설", null);
        insertMember(13L, 11L, null, null, null, "owner@new.co.kr", "홍길동",
                null, "OWNER", false, "ACTIVE", null, null);

        MyProfile profile = port.findByMemberId(13L).orElseThrow();

        assertThat(profile.teamId()).isNull();
        assertThat(profile.teamName()).isNull();
        assertThat(profile.roleLabel()).isNull();
        assertThat(profile.jobPositionId()).isNull();
        assertThat(profile.positionName()).isNull();
        assertThat(profile.isOnboarded()).isFalse();
        assertThat(profile.landingPath()).isEqualTo("/owner");
    }

    @Test
    @DisplayName("퇴사한 회원은 없는 것으로 본다 — 오프보딩 최종 승인으로 deleted_at 이 찍히면 조회되지 않는다")
    void resignedMemberIsNotFound() {
        insertCompany(21L, "VDRF-3Y5V", "(주)퇴사", "2026-01-01 09:00:00");
        insertMember(23L, 21L, null, null, null, "gone@x.co.kr", "이퇴사",
                null, "MEMBER", false, "RESIGNED", "2024-01-01", "2026-08-01 12:00:00");

        assertThat(port.findByMemberId(23L)).isEmpty();
    }

    @Test
    @DisplayName("구독 행이 없으면 plan 은 null — 무료 플랜으로 둘러대지 않는다. 결제 없이는 이용할 수 없다")
    void noSubscriptionMeansNoPlan() {
        insertCompany(31L, "1NK6-R3FF", "(주)무구독", "2026-02-02 09:00:00");
        insertMember(33L, 31L, null, null, null, "a@x.co.kr", "김무구독",
                null, "MEMBER", false, "ACTIVE", "2026-02-02", null);

        assertThat(port.findByMemberId(33L).orElseThrow().plan()).isNull();
    }

    @Test
    @DisplayName("해지된 구독만 있으면 plan 은 null — CANCELED 는 이용 중이 아니다")
    void canceledSubscriptionIsNotAPlan() {
        insertCompany(41L, "GZT5-KDF6", "(주)해지", "2026-03-03 09:00:00");
        insertSubscription(47L, 41L, "TEAM", "CANCELED");
        insertMember(43L, 41L, null, null, null, "b@x.co.kr", "박해지",
                null, "MEMBER", false, "ACTIVE", "2026-03-03", null);

        assertThat(port.findByMemberId(43L).orElseThrow().plan()).isNull();
    }

    @Test
    @DisplayName("없는 회원은 빈 값")
    void unknownMemberIsEmpty() {
        assertThat(port.findByMemberId(999_999L)).isEqualTo(Optional.empty());
    }

    private void insertCompany(Long id, String code, String name, String onboardedAt) {
        em.createNativeQuery("INSERT INTO company (id, code, name, onboarded_at) VALUES (?, ?, ?, ?)")
                .setParameter(1, id).setParameter(2, code).setParameter(3, name)
                .setParameter(4, onboardedAt)
                .executeUpdate();
    }

    private void insertTeam(Long id, String name) {
        em.createNativeQuery("INSERT INTO team (id, name) VALUES (?, ?)")
                .setParameter(1, id).setParameter(2, name).executeUpdate();
    }

    /** team_id 는 넣지 않는다 — 이 엔티티가 매핑하지 않아 H2 테스트 스키마에 없다(실 MySQL 에는 있다). */
    private void insertSubTeam(Long id, String name) {
        em.createNativeQuery("INSERT INTO sub_team (id, name) VALUES (?, ?)")
                .setParameter(1, id).setParameter(2, name).executeUpdate();
    }

    private void insertJobPosition(Long id, String name) {
        em.createNativeQuery("INSERT INTO job_position (id, name) VALUES (?, ?)")
                .setParameter(1, id).setParameter(2, name).executeUpdate();
    }

    private void insertSubscription(Long id, Long companyId, String plan, String status) {
        em.createNativeQuery("INSERT INTO subscription (id, company_id, plan, status) VALUES (?, ?, ?, ?)")
                .setParameter(1, id).setParameter(2, companyId)
                .setParameter(3, plan).setParameter(4, status).executeUpdate();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void insertMember(Long id, Long companyId, Long teamId, Long subTeamId, Long jobPositionId,
                             String email, String name, String phone, String role, boolean isAdmin,
                             String status, String joinedOn, String deletedAt) {
        em.createNativeQuery("""
                        INSERT INTO member
                          (id, company_id, team_id, sub_team_id, job_position_id, email, password_hash,
                           name, phone, role, is_admin, status, joined_on, deleted_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'hash', ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, id).setParameter(2, companyId).setParameter(3, teamId)
                .setParameter(4, subTeamId).setParameter(5, jobPositionId).setParameter(6, email)
                .setParameter(7, name).setParameter(8, phone).setParameter(9, role)
                .setParameter(10, isAdmin).setParameter(11, status)
                .setParameter(12, joinedOn).setParameter(13, deletedAt)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
