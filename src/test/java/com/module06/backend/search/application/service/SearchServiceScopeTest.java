package com.module06.backend.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.module06.backend.search.application.query.SearchQuery;
import com.module06.backend.search.application.result.SearchResult;
import com.module06.backend.search.domain.model.SearchType;
import com.module06.backend.search.infrastructure.persistence.SearchJdbcQueryAdapter;

/*
 * SR-1 검색 스코프가 앱단 후필터가 아니라 저장소 Query 조건으로 격리되는지 검증한다.
 */
@DisplayName("SR-1 통합 검색 스코프 격리")
class SearchServiceScopeTest {

    private SearchService service;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:search_scope;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ACTION;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        resetSchema();
        seedData();
        service = new SearchService(new SearchJdbcQueryAdapter(jdbcTemplate));
    }

    @Test
    @DisplayName("타 회사와 미참여 회의/액션/프로젝트, 접점 없는 사람은 결과와 count에서 제외된다")
    void excludesOutOfScopeRows() {
        SearchResult result = service.search(new SearchQuery(
                1L,
                10L,
                100L,
                "MEMBER",
                false,
                "Alpha",
                SearchType.ALL,
                List.of(),
                null,
                null,
                20
        ));

        assertThat(result.counts().meeting()).isEqualTo(1);
        assertThat(result.counts().action()).isEqualTo(2);
        assertThat(result.counts().project()).isEqualTo(1);
        assertThat(result.counts().person()).isEqualTo(2);
        assertThat(result.counts().all()).isEqualTo(6);

        assertThat(result.results())
                .extracting(item -> item.type() + ":" + item.id())
                .contains(
                        "MEETING:200",
                        "ACTION:300",
                        "ACTION:302",
                        "PROJECT:100",
                        "PERSON:11",
                        "PERSON:14"
                )
                .doesNotContain(
                        "MEETING:201",
                        "MEETING:202",
                        "ACTION:301",
                        "ACTION:303",
                        "ACTION:304",
                        "PROJECT:101",
                        "PROJECT:102",
                        "PERSON:12",
                        "PERSON:13"
                );
    }

    @Test
    @DisplayName("회의 스니펫은 meeting_summary.overview 원문을 반환한다")
    void returnsMeetingOverviewSnippet() {
        SearchResult result = service.search(new SearchQuery(
                1L,
                10L,
                100L,
                "MEMBER",
                false,
                "overview",
                SearchType.MEETING,
                List.of(),
                null,
                null,
                20
        ));

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).snippet()).isEqualTo("Alpha overview original text");
    }

    private void resetSchema() throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE meeting (
                        id BIGINT NOT NULL,
                        company_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        team_id BIGINT,
                        title VARCHAR(200) NOT NULL,
                        start_at DATETIME NOT NULL,
                        updated_at DATETIME NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE meeting_attendee (
                        meeting_id BIGINT NOT NULL,
                        member_id BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE meeting_summary (
                        id BIGINT NOT NULL,
                        company_id BIGINT NOT NULL,
                        meeting_id BIGINT NOT NULL,
                        overview TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE project (
                        id BIGINT NOT NULL,
                        company_id BIGINT NOT NULL,
                        tag VARCHAR(30) NOT NULL,
                        name VARCHAR(150) NOT NULL,
                        description TEXT,
                        color CHAR(7) NOT NULL,
                        deleted_at DATETIME,
                        updated_at DATETIME NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE project_team (
                        project_id BIGINT NOT NULL,
                        team_id BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE member (
                        id BIGINT NOT NULL,
                        company_id BIGINT NOT NULL,
                        team_id BIGINT,
                        job_position_id BIGINT,
                        name VARCHAR(50) NOT NULL,
                        role VARCHAR(20) NOT NULL,
                        deleted_at DATETIME,
                        updated_at DATETIME NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE job_position (
                        id BIGINT NOT NULL,
                        company_id BIGINT NOT NULL,
                        name VARCHAR(50) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE action (
                        id BIGINT NOT NULL,
                        company_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        team_id BIGINT,
                        assignee_member_id BIGINT,
                        action_type VARCHAR(20) NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        description TEXT,
                        due_date DATE NOT NULL,
                        updated_at DATETIME NOT NULL
                    )
                    """);
        }
    }

    private void seedData() {
        jdbcTemplate.update("INSERT INTO job_position VALUES (1, 1, 'Alpha Designer')");
        jdbcTemplate.update("INSERT INTO member VALUES (10, 1, 100, NULL, 'Requester', 'MEMBER', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO member VALUES (11, 1, 100, 1, 'Alpha Meeting Mate', 'MEMBER', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO member VALUES (12, 1, 200, NULL, 'Alpha Isolated', 'MEMBER', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO member VALUES (13, 2, 100, NULL, 'Alpha Other Company', 'MEMBER', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO member VALUES (14, 1, 100, NULL, 'Alpha Project Mate', 'MEMBER', NULL, '2026-08-09 09:00:00')");

        jdbcTemplate.update("INSERT INTO project VALUES (100, 1, 'alpha', 'Alpha Project', 'visible project', '#123456', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO project VALUES (101, 1, 'alpha-hidden', 'Alpha Hidden Project', 'wrong team', '#123456', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO project VALUES (102, 2, 'alpha-other', 'Alpha Other Project', 'wrong company', '#123456', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO project_team VALUES (100, 100)");
        jdbcTemplate.update("INSERT INTO project_team VALUES (101, 200)");
        jdbcTemplate.update("INSERT INTO project_team VALUES (102, 100)");

        jdbcTemplate.update("INSERT INTO meeting VALUES (200, 1, 100, 100, 'Alpha Meeting', '2026-08-09 09:00:00', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO meeting VALUES (201, 1, 100, 100, 'Alpha Unjoined Meeting', '2026-08-09 10:00:00', '2026-08-09 10:00:00')");
        jdbcTemplate.update("INSERT INTO meeting VALUES (202, 2, 102, 100, 'Alpha Other Company Meeting', '2026-08-09 11:00:00', '2026-08-09 11:00:00')");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (200, 10)");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (200, 11)");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (201, 11)");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (202, 10)");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (1, 1, 200, 'Alpha overview original text')");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (2, 1, 201, 'Alpha hidden overview')");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (3, 2, 202, 'Alpha other company overview')");

        jdbcTemplate.update("INSERT INTO action VALUES (300, 1, 100, NULL, 10, 'PERSONAL', 'Alpha Personal Action', 'visible personal', '2026-08-30', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (301, 1, 100, NULL, 11, 'PERSONAL', 'Alpha Other Personal Action', 'wrong assignee', '2026-08-30', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (302, 1, 100, 100, NULL, 'TEAM', 'Alpha Team Action', 'visible team', '2026-08-30', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (303, 1, 101, 200, NULL, 'TEAM', 'Alpha Other Team Action', 'wrong team', '2026-08-30', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (304, 2, 102, 100, NULL, 'TEAM', 'Alpha Other Company Action', 'wrong company', '2026-08-30', '2026-08-09 09:00:00')");
    }
}
