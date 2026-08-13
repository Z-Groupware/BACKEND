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

import com.module06.backend.meeting.application.port.out.TeamQueryPort;
import com.module06.backend.meeting.application.port.out.TeamQueryPort.TeamSnapshot;

/*
 * MEET-17 대시보드 카드용 팀 배치 조회다. 회사 경계를 여기서 자르고,
 * team.leader_member_id 를 계산 없이 그대로 노출한다.
 */
@SpringBootTest
@Transactional
@DisplayName("TeamQueryAdapter")
class TeamQueryAdapterTest {

    @Autowired
    private TeamQueryPort port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long COMPANY_ID = 9101L;
    private static final long OTHER_COMPANY_ID = 9102L;

    @BeforeEach
    void seedCompanies() {
        ensureCompanySeeded(COMPANY_ID);
        ensureCompanySeeded(OTHER_COMPANY_ID);
        ensureSystemRoleSeeded();
    }

    @Test
    @DisplayName("회사 안의 팀을 배치로 찾고, 이름과 팀장 id 를 함께 준다")
    void findsTeamsWithLeader() {
        seedTeam(9110L, COMPANY_ID, "개발팀");
        seedTeam(9111L, COMPANY_ID, "디자인팀");
        seedMember(91100L, 9110L, "김서준");
        assignLeader(9110L, 91100L);

        List<TeamSnapshot> found = port.findTeams(COMPANY_ID, List.of(9110L, 9111L));

        assertThat(found).hasSize(2);
        TeamSnapshot withLeader = snapshotOf(found, 9110L);
        assertThat(withLeader.teamName()).isEqualTo("개발팀");
        assertThat(withLeader.leaderMemberId()).isEqualTo(91100L);
        TeamSnapshot withoutLeader = snapshotOf(found, 9111L);
        assertThat(withoutLeader.teamName()).isEqualTo("디자인팀");
        assertThat(withoutLeader.leaderMemberId()).isNull();
    }

    @Test
    @DisplayName("다른 회사 팀은 결과에서 빠진다")
    void excludesTeamsFromOtherCompanies() {
        seedTeam(9120L, COMPANY_ID, "우리팀");
        seedTeam(9121L, OTHER_COMPANY_ID, "남의팀");

        List<TeamSnapshot> found = port.findTeams(COMPANY_ID, List.of(9120L, 9121L));

        assertThat(found).extracting(TeamSnapshot::teamId).containsExactly(9120L);
    }

    @Test
    @DisplayName("존재하지 않는 id 는 조용히 빠진다 — 전체를 실패시키지 않는다")
    void skipsUnknownIds() {
        seedTeam(9130L, COMPANY_ID, "있는팀");

        List<TeamSnapshot> found = port.findTeams(COMPANY_ID, List.of(9130L, 999_999L));

        assertThat(found).extracting(TeamSnapshot::teamId).containsExactly(9130L);
    }

    @Test
    @DisplayName("id 목록이 비어 있으면 조회 없이 빈 목록을 준다")
    void emptyIdsReturnsEmpty() {
        assertThat(port.findTeams(COMPANY_ID, List.of())).isEmpty();
    }

    @Test
    @DisplayName("id 목록이 null 이어도 빈 목록을 준다")
    void nullIdsReturnsEmpty() {
        assertThat(port.findTeams(COMPANY_ID, null)).isEmpty();
    }

    private TeamSnapshot snapshotOf(List<TeamSnapshot> found, long teamId) {
        return found.stream().filter(t -> t.teamId().equals(teamId)).findFirst().orElseThrow();
    }

    private void ensureCompanySeeded(Long companyId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company WHERE id = ?", Integer.class, companyId);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO company (id, code, name) VALUES (?, ?, ?)",
                    companyId, "TESTCO-" + companyId, "테스트회사");
        }
    }

    /**
     * member.role_id 는 FK 다. 테스트는 H2 를 Hibernate {@code create-drop} 으로 띄우고 Flyway 를
     * 끄기 때문에 운영 시드(V2.3.9, id 2 "없음")가 없다 — TeamMemberQueryAdapterTest 와 같은 방식으로
     * 여기서 직접 채운다.
     */
    private void ensureSystemRoleSeeded() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role WHERE id = 2", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO role (id, name) VALUES (2, '없음')");
        }
    }

    private void seedTeam(Long id, Long companyId, String name) {
        jdbcTemplate.update("INSERT INTO team (id, company_id, name) VALUES (?, ?, ?)", id, companyId, name);
    }

    private void seedMember(Long id, Long teamId, String name) {
        jdbcTemplate.update("""
                INSERT INTO member
                    (id, company_id, team_id, role_id, email, password_hash, name,
                     authority, is_admin, status, joined_on, deleted_at)
                VALUES (?, ?, ?, 2, ?, 'x', ?, 'MEMBER', false, 'ACTIVE', CURRENT_DATE, NULL)
                """, id, COMPANY_ID, teamId, id + "@test.com", name);
    }

    /** 팀장 지정은 조직 도메인의 쓰기 경로다 — 이 테스트는 그 결과 값이 그대로 노출되는지만 본다. */
    private void assignLeader(Long teamId, Long memberId) {
        jdbcTemplate.update("UPDATE team SET leader_member_id = ? WHERE id = ?", memberId, teamId);
    }
}
