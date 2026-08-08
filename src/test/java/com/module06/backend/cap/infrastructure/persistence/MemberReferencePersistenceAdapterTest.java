package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.repository.MemberReferenceRepository;
import com.module06.backend.cap.domain.repository.MemberReferenceRepository.MemberName;

/*
 * CAP-13 발신자 이름 조회 어댑터가 실제 member 테이블에서 배치로 이름을 읽어오는지 검증한다.
 * member.role_id는 FK라 identity.team.TeamMemberQueryAdapterTest와 동일한 방식으로 시드한다
 * (H2 test 스키마는 Flyway 시드 없이 Hibernate create-drop으로 뜬다).
 */
@SpringBootTest
@Transactional
@DisplayName("CAP-13 발신자 이름 조회 어댑터")
class MemberReferencePersistenceAdapterTest {

    private static final long COMPANY_ID = 9101L;

    @Autowired
    private MemberReferenceRepository memberReferenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM member WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM company WHERE id = ?", COMPANY_ID);
        jdbcTemplate.update("INSERT INTO company (id, code, name) VALUES (?, ?, ?)",
                COMPANY_ID, "TESTCO-" + COMPANY_ID, "테스트회사");
        ensureSystemRoleSeeded();
    }

    private void ensureSystemRoleSeeded() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role WHERE id = 2", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO role (id, name) VALUES (2, '없음')");
        }
    }

    // team_id는 nullable(V1)이라 팀 시딩 없이 NULL로 둔다.
    private void seedMember(Long id, String name) {
        jdbcTemplate.update("""
                INSERT INTO member
                    (id, company_id, team_id, role_id, email, password_hash, name,
                     authority, is_admin, status, joined_on, deleted_at)
                VALUES (?, ?, NULL, 2, ?, 'x', ?, 'MEMBER', false, 'ACTIVE', CURRENT_DATE, NULL)
                """,
                id, COMPANY_ID, id + "@test.com", name);
    }

    /* 여러 id를 한 번에 배치로 조회하는지, 없는 id는 결과에서 그냥 빠지는지 검증한다. */
    @Test
    @DisplayName("여러 발신자 이름을 배치로 조회한다")
    void findsNamesInBatch() {
        seedMember(91001L, "김서준");
        seedMember(91002L, "강서연");

        List<MemberName> found = memberReferenceRepository.findNames(List.of(91001L, 91002L, 999_999L));

        assertThat(found).extracting(MemberName::memberId, MemberName::name)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(91001L, "김서준"),
                        org.assertj.core.groups.Tuple.tuple(91002L, "강서연"));
    }

    /* 빈 id 목록이면 쿼리를 아예 안 던지고 빈 목록을 반환하는지 검증한다. */
    @Test
    @DisplayName("빈 id 목록이면 빈 목록을 반환한다")
    void returnsEmptyForEmptyInput() {
        assertThat(memberReferenceRepository.findNames(List.of())).isEmpty();
    }
}
