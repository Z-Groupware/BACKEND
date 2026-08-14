package com.module06.backend.identity.member.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.domain.model.Role;
import com.module06.backend.identity.member.domain.repository.RoleRepository;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/* 역할(구 sub_team) 쓰기 창구 — §4-1 온보딩 커밋과 §6-10~6-12 역할 CRUD 가 함께 쓴다. */
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

    @Test
    @DisplayName("회사·부서 스코프로만 찾는다 — 남의 회사·다른 부서 조건이면 못 찾는다")
    void findsOnlyWithinCompanyAndTeam() {
        insertCompany(702L);
        insertTeam(72L, 702L, "개발팀");
        insertTeam(73L, 702L, "플랫폼팀");
        Long roleId = roleRepository.create(702L, 72L, "백엔드");
        em.flush();
        em.clear();

        assertThat(roleRepository.findByIdAndCompanyIdAndTeamId(roleId, 702L, 72L))
                .get().extracting(Role::name).isEqualTo("백엔드");
        assertThat(roleRepository.findByIdAndCompanyIdAndTeamId(roleId, 702L, 73L)).isEmpty();
        assertThat(roleRepository.findByIdAndCompanyIdAndTeamId(roleId, 999L, 72L)).isEmpty();
    }

    /* 시스템 역할(id 1 리더 · 2 없음, V2.3.9)은 company_id·team_id 가 NULL 이라 편집 대상으로 안 잡힌다. */
    @Test
    @DisplayName("시스템 역할은 회사·부서 스코프 조회에 잡히지 않는다")
    void systemRolesAreNotEditable() {
        insertCompany(703L);
        insertTeam(74L, 703L, "개발팀");

        assertThat(roleRepository.findByIdAndCompanyIdAndTeamId(1L, 703L, 74L)).isEmpty();
        assertThat(roleRepository.findByIdAndCompanyIdAndTeamId(2L, 703L, 74L)).isEmpty();
    }

    @Test
    @DisplayName("이름 중복 검사는 부서 단위로 본다 — 다른 부서의 같은 이름은 걸리지 않는다")
    void nameDuplicationIsScopedToTeam() {
        insertCompany(704L);
        insertTeam(75L, 704L, "개발팀");
        insertTeam(76L, 704L, "플랫폼팀");
        roleRepository.create(704L, 75L, "백엔드");
        em.flush();
        em.clear();

        assertThat(roleRepository.existsByTeamIdAndName(75L, "백엔드")).isTrue();
        assertThat(roleRepository.existsByTeamIdAndName(76L, "백엔드")).isFalse();
    }

    @Test
    @DisplayName("이름을 바꾸고 지운다")
    void renamesAndDeletes() {
        insertCompany(705L);
        insertTeam(77L, 705L, "개발팀");
        Long roleId = roleRepository.create(705L, 77L, "백엔드");
        em.flush();
        em.clear();

        roleRepository.rename(roleId, "서버");
        em.flush();
        em.clear();
        assertThat(roleRepository.findByIdAndCompanyIdAndTeamId(roleId, 705L, 77L))
                .get().extracting(Role::name).isEqualTo("서버");

        roleRepository.delete(roleId);
        em.flush();
        em.clear();
        assertThat(roleRepository.findByIdAndCompanyIdAndTeamId(roleId, 705L, 77L)).isEmpty();
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
