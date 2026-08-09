package com.module06.backend.identity.team.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.team.application.port.out.TeamMemberQueryPort;
import com.module06.backend.identity.team.application.port.out.TeamMemberQueryPort.TeamMemberSummary;

@SpringBootTest
@Transactional
@DisplayName("Team-Member 크로스 도메인 읽기 어댑터")
class TeamMemberQueryAdapterTest {

    @Autowired
    private TeamMemberQueryPort port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long COMPANY_ID = 9001L;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM member WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM company WHERE id = ?", COMPANY_ID);
        jdbcTemplate.update(
                "INSERT INTO company (id, code, name) VALUES (?, ?, ?)",
                COMPANY_ID, "TESTCO-" + COMPANY_ID, "테스트회사");
        ensureSystemRoleSeeded();
    }

    /**
     * member.role_id 는 FK다. 운영 스키마는 V2.3.9 가 id 2("없음")를 시드하지만, 테스트는
     * H2 를 Hibernate {@code create-drop} 으로 띄우고 Flyway 는 끈다(MySQL 전용 DDL이라
     * H2 에 안 맞음 — src/test/resources/application.yaml 주석 참고). 그래서 이 테스트
     * 컨텍스트에는 그 시드가 없다 — MyProfileQueryAdapterTest 가 role_id FK 를 쓸 때 쓰는
     * 것과 같은 방식으로 여기서도 직접 채워 넣는다. 여러 테스트 메서드가 같은 컨텍스트를
     * 공유하므로 중복 삽입을 막기 위해 존재 여부를 먼저 확인한다.
     */
    private void ensureSystemRoleSeeded() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role WHERE id = 2", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO role (id, name) VALUES (2, '없음')");
        }
    }

    /**
     * member.team_id 도 FK 다 — 운영 MySQL 스키마(V1)에는 이 제약이 없지만(팀은 아직 없어도
     * 되는 소속이라 의도적으로 뺐다), 이 테스트가 도는 H2 스키마는 Hibernate 가
     * {@code MemberJpaEntity.team}(@ManyToOne → TeamRefEntity) 매핑에서 FK 를 만들어 넣는다.
     * 그래서 team 행을 먼저 채워 넣어야 한다. role 시딩과 같은 이유·같은 방식이다.
     */
    private void ensureTeamSeeded(Long teamId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team WHERE id = ?", Integer.class, teamId);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO team (id, name) VALUES (?, ?)", teamId, "팀" + teamId);
        }
    }

    private void seedMember(Long id, Long teamId, String name, boolean resigned) {
        ensureTeamSeeded(teamId);
        jdbcTemplate.update("""
                INSERT INTO member
                    (id, company_id, team_id, role_id, email, password_hash, name,
                     authority, is_admin, status, joined_on, deleted_at)
                VALUES (?, ?, ?, 2, ?, 'x', ?, 'MEMBER', false, 'ACTIVE', CURRENT_DATE,
                        %s)
                """.formatted(resigned ? "NOW()" : "NULL"),
                id, COMPANY_ID, teamId, id + "@test.com", name);
    }

    @Test
    @DisplayName("퇴사자를 뺀 활성 구성원만 회사 단위로 모아온다")
    void findsOnlyActiveMembersByCompany() {
        seedMember(90001L, 1L, "김서준", false);
        seedMember(90002L, 1L, "강서연", false);
        seedMember(90003L, 1L, "퇴사자", true);

        List<TeamMemberSummary> members = port.findActiveMembersByCompany(COMPANY_ID);

        assertThat(members).extracting(TeamMemberSummary::name)
                .containsExactlyInAnyOrder("김서준", "강서연");
    }

    @Test
    @DisplayName("팀에 활성 구성원이 있는지 확인한다 — 퇴사자만 있으면 없다고 답한다")
    void hasActiveMembersIgnoresResigned() {
        seedMember(90004L, 5L, "이지훈", true);

        assertThat(port.hasActiveMembers(5L)).isFalse();

        seedMember(90005L, 5L, "박민재", false);

        assertThat(port.hasActiveMembers(5L)).isTrue();
    }
}
