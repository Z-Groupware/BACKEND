package com.module06.backend.identity.member.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.domain.model.Role;
import com.module06.backend.identity.member.domain.repository.RoleRepository;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /*
     * 사전 검사(TeamRoleService)와 INSERT 사이에 다른 요청이 끼어들 수 있어, 최종 관문은
     * UK_ROLE_TEAM_NAME(V2.3.23)이다. 제약 위반이 500 이 아니라 공개 계약인 에러 코드로
     * 나오는지를 여기서 못박는다.
     */
    @Test
    @DisplayName("같은 부서에 같은 이름을 또 만들면 제약이 막고 ROLE_NAME_DUPLICATED 로 나온다")
    void translatesNameUniqueViolation() {
        insertCompany(706L);
        insertTeam(78L, 706L, "개발팀");
        roleRepository.create(706L, 78L, "백엔드");
        em.flush();

        assertThatThrownBy(() -> roleRepository.create(706L, 78L, "백엔드"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("다른 부서라면 같은 이름이어도 제약에 걸리지 않는다")
    void allowsSameNameInAnotherTeam() {
        insertCompany(707L);
        insertTeam(79L, 707L, "개발팀");
        insertTeam(80L, 707L, "플랫폼팀");
        roleRepository.create(707L, 79L, "백엔드");
        em.flush();

        assertThat(roleRepository.create(707L, 80L, "백엔드")).isNotNull();
    }

    @Test
    @DisplayName("이름 변경으로 같은 부서 안 이름이 겹쳐도 제약이 막는다")
    void translatesNameUniqueViolationOnRename() {
        insertCompany(708L);
        insertTeam(81L, 708L, "개발팀");
        roleRepository.create(708L, 81L, "백엔드");
        Long frontend = roleRepository.create(708L, 81L, "프론트엔드");
        em.flush();
        em.clear();

        assertThatThrownBy(() -> roleRepository.rename(frontend, "백엔드"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NAME_DUPLICATED);
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
