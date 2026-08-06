package com.module06.backend.identity.member.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.application.dto.MemberCredentials;
import com.module06.backend.identity.member.application.port.out.MemberAuthQueryPort;
import com.module06.backend.identity.member.domain.model.Authority;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemberAuthQueryAdapter")
@SpringBootTest
@Transactional
class MemberAuthQueryAdapterTest {

    @Autowired
    private MemberAuthQueryPort port;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("회사와 이메일로 로그인 재료를 찾는다")
    void findsCredentials() {
        insertCompany(101L, "8AS2-G8T1", "(주)테크스타트");
        insertTeam(2L, "개발팀");
        insertMember(103L, 101L, 2L, "hayun@zgroup.co.kr", "$2a$10$hash", "LEADER", true, "ACTIVE", null);

        MemberCredentials found = port.findForLogin(101L, "hayun@zgroup.co.kr").orElseThrow();

        assertThat(found.memberId()).isEqualTo(103L);
        assertThat(found.companyId()).isEqualTo(101L);
        assertThat(found.passwordHash()).isEqualTo("$2a$10$hash");
        assertThat(found.authority()).isEqualTo(Authority.LEADER);
        assertThat(found.isAdmin()).isTrue();
        assertThat(found.teamId()).isEqualTo(2L);
        assertThat(found.resigned()).isFalse();
    }

    @Test
    @DisplayName("같은 이메일이라도 다른 회사면 찾지 않는다 — 이메일은 회사 안에서만 유일하다")
    void doesNotCrossCompanyBoundary() {
        insertCompany(111L, "1NK6-R3FF", "(주)가");
        insertCompany(112L, "S0ZW-MGTC", "(주)나");
        insertMember(113L, 111L, null, "same@mail.com", "hashA", "MEMBER", false, "ACTIVE", null);

        assertThat(port.findForLogin(112L, "same@mail.com")).isEmpty();
        assertThat(port.findForLogin(111L, "same@mail.com")).isPresent();
    }

    @Test
    @DisplayName("퇴사자도 찾되 resigned 로 표시한다 — 로그인 쪽이 '비번 틀림'과 '퇴사'를 구분해 답해야 한다")
    void marksResignedInsteadOfHiding() {
        insertCompany(121L, "VDRF-3Y5V", "(주)퇴사");
        insertMember(123L, 121L, null, "gone@x.co.kr", "hashB", "MEMBER", false,
                "RESIGNED", "2026-08-01 12:00:00");

        MemberCredentials found = port.findForLogin(121L, "gone@x.co.kr").orElseThrow();

        assertThat(found.resigned()).isTrue();
    }

    @Test
    @DisplayName("없는 이메일은 빈 값")
    void unknownEmailIsEmpty() {
        assertThat(port.findForLogin(999L, "nobody@x.co.kr")).isEmpty();
    }

    private void insertTeam(Long id, String name) {
        em.createNativeQuery("INSERT INTO team (id, name) VALUES (?, ?)")
                .setParameter(1, id).setParameter(2, name).executeUpdate();
    }

    private void insertCompany(Long id, String code, String name) {
        em.createNativeQuery("INSERT INTO company (id, code, name) VALUES (?, ?, ?)")
                .setParameter(1, id).setParameter(2, code).setParameter(3, name).executeUpdate();
    }

    private void insertMember(Long id, Long companyId, Long teamId, String email, String passwordHash,
                              String role, boolean isAdmin, String status, String deletedAt) {
        /* role_id 는 NOT NULL 이다(V2.3.10) — 시드 행 "없음"(id 2)을 그대로 흉내 낸다. */
        em.createNativeQuery("MERGE INTO role (id, name) KEY(id) VALUES (2, '없음')").executeUpdate();
        em.createNativeQuery("""
                        INSERT INTO member
                          (id, company_id, team_id, role_id, email, password_hash, name, authority, is_admin, status, deleted_at)
                        VALUES (?, ?, ?, 2, ?, ?, '테스트', ?, ?, ?, ?)
                        """)
                .setParameter(1, id).setParameter(2, companyId).setParameter(3, teamId)
                .setParameter(4, email).setParameter(5, passwordHash).setParameter(6, role)
                .setParameter(7, isAdmin).setParameter(8, status).setParameter(9, deletedAt)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
