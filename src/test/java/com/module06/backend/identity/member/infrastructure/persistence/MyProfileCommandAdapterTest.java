package com.module06.backend.identity.member.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.application.port.out.MyProfileCommandPort;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/* 마이페이지 셀프 프로필 수정 — null 인 인자는 값을 바꾸지 않는다(부분 수정). */
@DisplayName("MyProfileCommandAdapter")
@SpringBootTest
@Transactional
class MyProfileCommandAdapterTest {

    @Autowired
    private MyProfileCommandPort port;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("전화번호만 보내면 부서·직급은 그대로다")
    void partialUpdateOnlyTouchesSentFields() {
        insertCompany(601L, "(주)테스트");
        insertTeam(61L, "개발팀");
        insertTeam(62L, "디자인팀");
        insertPosition(64L, 601L, "선임");
        insertMember(603L, 601L, 61L, 64L, "010-1000-0000");

        port.updateProfile(603L, null, null, "010-9999-0000");
        em.flush();
        em.clear();

        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT team_id, position_id, phone FROM member WHERE id = ?")
                .setParameter(1, 603L)
                .getSingleResult();
        assertThat(((Number) row[0]).longValue()).isEqualTo(61L);
        assertThat(((Number) row[1]).longValue()).isEqualTo(64L);
        assertThat(row[2]).isEqualTo("010-9999-0000");
    }

    @Test
    @DisplayName("팀·직급·전화번호를 모두 바꾼다")
    void updatesAllThreeFields() {
        insertCompany(611L, "(주)테스트2");
        insertTeam(71L, "개발팀");
        insertTeam(72L, "디자인팀");
        insertPosition(74L, 611L, "선임");
        insertPosition(75L, 611L, "책임");
        insertMember(613L, 611L, 71L, 74L, "010-1000-0001");

        port.updateProfile(613L, 72L, 75L, "010-8888-0000");
        em.flush();
        em.clear();

        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT team_id, position_id, phone FROM member WHERE id = ?")
                .setParameter(1, 613L)
                .getSingleResult();
        assertThat(((Number) row[0]).longValue()).isEqualTo(72L);
        assertThat(((Number) row[1]).longValue()).isEqualTo(75L);
        assertThat(row[2]).isEqualTo("010-8888-0000");
    }

    private void insertCompany(Long id, String name) {
        em.createNativeQuery("INSERT INTO company (id, code, name) VALUES (?, ?, ?)")
                .setParameter(1, id).setParameter(2, "C" + id).setParameter(3, name)
                .executeUpdate();
    }

    private void insertTeam(Long id, String name) {
        em.createNativeQuery("INSERT INTO team (id, name) VALUES (?, ?)")
                .setParameter(1, id).setParameter(2, name).executeUpdate();
    }

    private void insertPosition(Long id, Long companyId, String name) {
        em.createNativeQuery("INSERT INTO position (id, company_id, name) VALUES (?, ?, ?)")
                .setParameter(1, id).setParameter(2, companyId).setParameter(3, name)
                .executeUpdate();
    }

    private void insertMember(Long id, Long companyId, Long teamId, Long positionId, String phone) {
        em.createNativeQuery("MERGE INTO role (id, name) KEY(id) VALUES (2, '없음')").executeUpdate();
        em.createNativeQuery("""
                        INSERT INTO member
                          (id, company_id, team_id, role_id, position_id, email, password_hash, name, phone,
                           authority, is_admin, status, joined_on)
                        VALUES (?, ?, ?, 2, ?, ?, 'hash', ?, ?, 'MEMBER', FALSE, 'ACTIVE', '2022-05-10')
                        """)
                .setParameter(1, id).setParameter(2, companyId).setParameter(3, teamId)
                .setParameter(4, positionId).setParameter(5, "m" + id + "@x.co.kr")
                .setParameter(6, "테스트" + id).setParameter(7, phone)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
