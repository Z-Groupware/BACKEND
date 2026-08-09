package com.module06.backend.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
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
                "Alpha overview",
                SearchType.MEETING,
                List.of(),
                null,
                null,
                20
        ));

        assertThat(result.results())
                .filteredOn(item -> item.type() == SearchType.MEETING && item.id() == 200L)
                .singleElement()
                .satisfies(item -> assertThat(item.snippet()).isEqualTo("Alpha overview original text"));
    }

    @Test
    @DisplayName("사람 결과의 role은 사이트 권한이 아니라 직책(job_position.name)을 반환하고, 직책이 없으면 null이다")
    void personRoleReturnsJobPositionName() {
        SearchResult result = service.search(new SearchQuery(
                1L,
                10L,
                100L,
                "MEMBER",
                false,
                "Alpha",
                SearchType.PERSON,
                List.of(),
                null,
                null,
                20
        ));

        assertThat(result.results())
                .filteredOn(item -> item.type() == SearchType.PERSON && item.id() == 11L)
                .singleElement()
                .satisfies(item -> assertThat(item.role()).isEqualTo("Alpha Designer"));

        assertThat(result.results())
                .filteredOn(item -> item.type() == SearchType.PERSON && item.id() == 14L)
                .singleElement()
                .satisfies(item -> assertThat(item.role()).isNull());
    }

    @Test
    @DisplayName("tags는 project.tag 기준으로 회의/액션/프로젝트에 적용하고 사람은 제외한다")
    void filtersBySingleProjectTagAndExcludesPerson() {
        SearchResult result = service.search(new SearchQuery(
                1L,
                10L,
                100L,
                "MEMBER",
                false,
                "Alpha",
                SearchType.ALL,
                List.of(" alpha "),
                null,
                null,
                20
        ));

        assertThat(result.counts().meeting()).isEqualTo(1);
        assertThat(result.counts().action()).isEqualTo(2);
        assertThat(result.counts().project()).isEqualTo(1);
        assertThat(result.counts().person()).isZero();
        assertThat(result.counts().all()).isEqualTo(4);

        assertThat(result.results())
                .extracting(item -> item.type() + ":" + item.id())
                .containsExactlyInAnyOrder("MEETING:200", "ACTION:300", "ACTION:302", "PROJECT:100");
    }

    @Test
    @DisplayName("tags는 다중 태그 OR 조건으로 필터링하고 불일치 태그는 결과/count에서 제외한다")
    void filtersByMultipleProjectTags() {
        SearchResult result = service.search(new SearchQuery(
                1L,
                10L,
                100L,
                "MEMBER",
                false,
                "Filter",
                SearchType.ALL,
                List.of("PRD", "ENG"),
                null,
                null,
                20
        ));

        assertThat(result.counts().meeting()).isEqualTo(3);
        assertThat(result.counts().action()).isEqualTo(4);
        assertThat(result.counts().project()).isEqualTo(2);
        assertThat(result.counts().person()).isZero();
        assertThat(result.counts().all()).isEqualTo(9);

        assertThat(result.results())
                .extracting(item -> item.type() + ":" + item.id())
                .contains(
                        "MEETING:203",
                        "MEETING:204",
                        "MEETING:205",
                        "ACTION:305",
                        "ACTION:306",
                        "ACTION:307",
                        "ACTION:308",
                        "PROJECT:103",
                        "PROJECT:104"
                )
                .doesNotContain("MEETING:206", "PROJECT:105");
    }

    @Test
    @DisplayName("from만 있으면 회의 start_at과 액션 due_date의 시작 경계를 포함한다")
    void filtersByFromInclusive() {
        SearchResult result = searchFilterWithDates(LocalDate.of(2026, 8, 15), null);

        assertThat(result.counts().meeting()).isEqualTo(2);
        assertThat(result.counts().action()).isEqualTo(3);
        assertThat(result.counts().project()).isEqualTo(2);
        assertThat(result.counts().all()).isEqualTo(7);
        assertThat(result.results())
                .extracting(item -> item.type() + ":" + item.id())
                .contains("MEETING:204", "ACTION:306", "ACTION:308")
                .doesNotContain("MEETING:203", "ACTION:305");
    }

    @Test
    @DisplayName("to만 있으면 회의 start_at과 액션 due_date의 종료 경계를 포함한다")
    void filtersByToInclusive() {
        SearchResult result = searchFilterWithDates(null, LocalDate.of(2026, 8, 15));

        assertThat(result.counts().meeting()).isEqualTo(2);
        assertThat(result.counts().action()).isEqualTo(2);
        assertThat(result.counts().project()).isEqualTo(2);
        assertThat(result.counts().all()).isEqualTo(6);
        assertThat(result.results())
                .extracting(item -> item.type() + ":" + item.id())
                .contains("MEETING:203", "MEETING:204", "ACTION:305", "ACTION:306")
                .doesNotContain("MEETING:205", "ACTION:307", "ACTION:308");
    }

    @Test
    @DisplayName("from/to가 모두 있으면 양쪽 경계를 포함하고 프로젝트는 기간 필터 대상에서 제외한다")
    void filtersByDateRangeInclusive() {
        SearchResult result = searchFilterWithDates(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result.counts().meeting()).isEqualTo(3);
        assertThat(result.counts().action()).isEqualTo(3);
        assertThat(result.counts().project()).isEqualTo(2);
        assertThat(result.counts().all()).isEqualTo(8);
        assertThat(result.results())
                .extracting(item -> item.type() + ":" + item.id())
                .contains("MEETING:203", "MEETING:205", "ACTION:305", "ACTION:307", "PROJECT:103", "PROJECT:104")
                .doesNotContain("ACTION:308");
    }

    @Test
    @DisplayName("태그/기간/타입 스코프를 동시에 적용한다")
    void filtersByTagDateAndRequestedTypeTogether() {
        SearchResult result = service.search(new SearchQuery(
                1L,
                10L,
                100L,
                "MEMBER",
                false,
                "Filter",
                SearchType.ACTION,
                List.of("eng"),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                20
        ));

        assertThat(result.counts().meeting()).isZero();
        assertThat(result.counts().action()).isEqualTo(1);
        assertThat(result.counts().project()).isZero();
        assertThat(result.counts().person()).isZero();
        assertThat(result.counts().all()).isEqualTo(1);
        assertThat(result.results())
                .extracting(item -> item.type() + ":" + item.id())
                .containsExactly("ACTION:306");
    }

    private SearchResult searchFilterWithDates(LocalDate from, LocalDate to) {
        return service.search(new SearchQuery(
                1L,
                10L,
                100L,
                "MEMBER",
                false,
                "Filter",
                SearchType.ALL,
                List.of(),
                from,
                to,
                20
        ));
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
        jdbcTemplate.update("INSERT INTO project VALUES (103, 1, 'prd', 'Filter Product Project', 'visible product project', '#654321', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO project VALUES (104, 1, 'eng', 'Filter Engineering Project', 'visible engineering project', '#654321', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO project VALUES (105, 1, 'sales', 'Filter Sales Project', 'wrong team', '#654321', NULL, '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO project_team VALUES (100, 100)");
        jdbcTemplate.update("INSERT INTO project_team VALUES (101, 200)");
        jdbcTemplate.update("INSERT INTO project_team VALUES (102, 100)");
        jdbcTemplate.update("INSERT INTO project_team VALUES (103, 100)");
        jdbcTemplate.update("INSERT INTO project_team VALUES (104, 100)");
        jdbcTemplate.update("INSERT INTO project_team VALUES (105, 200)");

        jdbcTemplate.update("INSERT INTO meeting VALUES (200, 1, 100, 100, 'Alpha Meeting', '2026-08-09 09:00:00', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO meeting VALUES (201, 1, 100, 100, 'Alpha Unjoined Meeting', '2026-08-09 10:00:00', '2026-08-09 10:00:00')");
        jdbcTemplate.update("INSERT INTO meeting VALUES (202, 2, 102, 100, 'Alpha Other Company Meeting', '2026-08-09 11:00:00', '2026-08-09 11:00:00')");
        jdbcTemplate.update("INSERT INTO meeting VALUES (203, 1, 103, 100, 'Filter Product Kickoff', '2026-08-01 09:00:00', '2026-08-01 09:00:00')");
        jdbcTemplate.update("INSERT INTO meeting VALUES (204, 1, 104, 100, 'Filter Engineering Review', '2026-08-15 09:00:00', '2026-08-15 09:00:00')");
        jdbcTemplate.update("INSERT INTO meeting VALUES (205, 1, 103, 100, 'Filter Product Boundary', '2026-08-31 23:59:00', '2026-08-31 23:59:00')");
        jdbcTemplate.update("INSERT INTO meeting VALUES (206, 1, 105, 200, 'Filter Sales Hidden', '2026-08-15 09:00:00', '2026-08-15 09:00:00')");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (200, 10)");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (200, 11)");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (201, 11)");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (202, 10)");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (203, 10)");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (204, 10)");
        jdbcTemplate.update("INSERT INTO meeting_attendee VALUES (205, 10)");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (1, 1, 200, 'Alpha overview original text')");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (2, 1, 201, 'Alpha hidden overview')");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (3, 2, 202, 'Alpha other company overview')");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (4, 1, 203, 'Filter product overview')");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (5, 1, 204, 'Filter engineering overview')");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (6, 1, 205, 'Filter boundary overview')");
        jdbcTemplate.update("INSERT INTO meeting_summary VALUES (7, 1, 206, 'Filter hidden overview')");

        jdbcTemplate.update("INSERT INTO action VALUES (300, 1, 100, NULL, 10, 'PERSONAL', 'Alpha Personal Action', 'visible personal', '2026-08-30', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (301, 1, 100, NULL, 11, 'PERSONAL', 'Alpha Other Personal Action', 'wrong assignee', '2026-08-30', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (302, 1, 100, 100, NULL, 'TEAM', 'Alpha Team Action', 'visible team', '2026-08-30', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (303, 1, 101, 200, NULL, 'TEAM', 'Alpha Other Team Action', 'wrong team', '2026-08-30', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (304, 2, 102, 100, NULL, 'TEAM', 'Alpha Other Company Action', 'wrong company', '2026-08-30', '2026-08-09 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (305, 1, 103, NULL, 10, 'PERSONAL', 'Filter Product Personal Action', 'visible personal', '2026-08-01', '2026-08-01 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (306, 1, 104, 100, NULL, 'TEAM', 'Filter Engineering Team Action', 'visible team', '2026-08-15', '2026-08-15 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (307, 1, 103, NULL, 10, 'PERSONAL', 'Filter Product Boundary Action', 'visible boundary', '2026-08-31', '2026-08-31 09:00:00')");
        jdbcTemplate.update("INSERT INTO action VALUES (308, 1, 104, 100, NULL, 'TEAM', 'Filter Engineering Later Action', 'visible later', '2026-09-01', '2026-09-01 09:00:00')");
    }
}
