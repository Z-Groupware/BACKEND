package com.module06.backend.identity.member.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.domain.repository.RoleRepository;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/* §4-1 온보딩 커밋 전용 — 역할(구 sub_team)을 팀 아래에 만든다. */
@DisplayName("RolePersistenceAdapter")
@SpringBootTest
@Transactional
class RolePersistenceAdapterTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("companyId·teamId·name 을 그대로 저장하고 생성된 id를 돌려준다")
    void createsRoleScopedToTeam() {
        insertCompany(701L);
        insertTeam(71L, 701L, "개발팀");

        Long roleId = roleRepository.create(701L, 71L, "백엔드");
        em.flush();
        em.clear();

        assertThat(roleId).isNotNull();
        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT company_id, team_id, name FROM role WHERE id = ?")
                .setParameter(1, roleId)
                .getSingleResult();
        assertThat(((Number) row[0]).longValue()).isEqualTo(701L);
        assertThat(((Number) row[1]).longValue()).isEqualTo(71L);
        assertThat(row[2]).isEqualTo("백엔드");
    }

    private void insertCompany(Long id) {
        em.createNativeQuery("INSERT INTO company (id, code, name) VALUES (?, ?, '(주)테스트')")
                .setParameter(1, id).setParameter(2, "C" + id).executeUpdate();
    }

    private void insertTeam(Long id, Long companyId, String name) {
        em.createNativeQuery("INSERT INTO team (id, company_id, name) VALUES (?, ?, ?)")
                .setParameter(1, id).setParameter(2, companyId).setParameter(3, name)
                .executeUpdate();
    }
}
