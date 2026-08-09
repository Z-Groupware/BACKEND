package com.module06.backend.search.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.search.domain.model.SearchType;
import com.module06.backend.search.domain.repository.SearchQueryRepository;
import com.module06.backend.search.domain.repository.SearchQueryRepository.Project;
import com.module06.backend.search.domain.repository.SearchQueryRepository.SearchHit;
import com.module06.backend.search.domain.repository.SearchQueryRepository.SearchScope;

/*
 * SR-1 통합 검색용 읽기 전용 JDBC 어댑터다.
 *
 * 각 도메인 엔티티를 검색 도메인에 재매핑하지 않고, 회사 격리와 참여 스코프를 SQL WHERE 절에서 직접 강제한다.
 * 특히 action 도메인의 Java 구현은 아직 스텁이므로 명세에 따라 action 테이블을 SELECT 전용으로 조회한다.
 */
@Component
@RequiredArgsConstructor
public class SearchJdbcQueryAdapter implements SearchQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public long count(SearchScope scope, SearchType type, String keyword) {
        return switch (type) {
            case MEETING -> countMeeting(scope, keyword);
            case ACTION -> countAction(scope, keyword);
            case PROJECT -> countProject(scope, keyword);
            case PERSON -> countPerson(scope, keyword);
            case ALL -> throw new IllegalArgumentException("ALL count is assembled by service.");
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> search(SearchScope scope, SearchType type, String keyword, int limit) {
        return switch (type) {
            case MEETING -> searchMeeting(scope, keyword, limit);
            case ACTION -> searchAction(scope, keyword, limit);
            case PROJECT -> searchProject(scope, keyword, limit);
            case PERSON -> searchPerson(scope, keyword, limit);
            case ALL -> throw new IllegalArgumentException("ALL search is assembled by service.");
        };
    }

    private long countMeeting(SearchScope scope, String keyword) {
        String sql = """
                SELECT COUNT(*)
                  FROM meeting m
                  JOIN meeting_attendee mas
                    ON mas.meeting_id = m.id
                   AND mas.member_id = ?
                  LEFT JOIN meeting_summary ms
                    ON ms.meeting_id = m.id
                   AND ms.company_id = m.company_id
                 WHERE m.company_id = ?
                   AND (LOWER(m.title) LIKE ? OR LOWER(COALESCE(ms.overview, '')) LIKE ?)
                """;
        return queryCount(sql, scope.requesterMemberId(), scope.companyId(), like(keyword), like(keyword));
    }

    private List<SearchHit> searchMeeting(SearchScope scope, String keyword, int limit) {
        String sql = """
                SELECT 'MEETING' AS result_type,
                       m.id AS id,
                       m.title AS title,
                       COALESCE(ms.overview, '') AS snippet,
                       p.id AS project_id,
                       p.tag AS project_tag,
                       p.name AS project_name,
                       p.color AS project_color,
                       CAST(m.start_at AS DATE) AS date_value,
                       NULL AS role_value,
                       CASE
                           WHEN LOWER(m.title) = ? THEN 100
                           WHEN LOWER(m.title) LIKE ? THEN 80
                           WHEN LOWER(COALESCE(ms.overview, '')) LIKE ? THEN 30
                           ELSE 0
                       END AS score_value,
                       m.start_at AS sort_value
                  FROM meeting m
                  JOIN meeting_attendee mas
                    ON mas.meeting_id = m.id
                   AND mas.member_id = ?
                  LEFT JOIN meeting_summary ms
                    ON ms.meeting_id = m.id
                   AND ms.company_id = m.company_id
                  LEFT JOIN project p
                    ON p.id = m.project_id
                   AND p.company_id = m.company_id
                 WHERE m.company_id = ?
                   AND (LOWER(m.title) LIKE ? OR LOWER(COALESCE(ms.overview, '')) LIKE ?)
                 ORDER BY score_value DESC, sort_value DESC, m.id ASC
                 LIMIT ?
                """;
        return jdbcTemplate.query(sql, this::mapItem,
                exact(keyword), prefix(keyword), like(keyword),
                scope.requesterMemberId(), scope.companyId(), like(keyword), like(keyword), limit);
    }

    private long countAction(SearchScope scope, String keyword) {
        String sql = """
                SELECT COUNT(*)
                  FROM action a
                 WHERE a.company_id = ?
                   AND ((a.action_type = 'PERSONAL' AND a.assignee_member_id = ?)
                        OR (a.action_type = 'TEAM' AND a.team_id = ?))
                   AND (LOWER(a.title) LIKE ? OR LOWER(COALESCE(a.description, '')) LIKE ?)
                """;
        return queryCount(sql, scope.companyId(), scope.requesterMemberId(), scope.requesterTeamId(), like(keyword), like(keyword));
    }

    private List<SearchHit> searchAction(SearchScope scope, String keyword, int limit) {
        String sql = """
                SELECT 'ACTION' AS result_type,
                       a.id AS id,
                       a.title AS title,
                       COALESCE(a.description, '') AS snippet,
                       p.id AS project_id,
                       p.tag AS project_tag,
                       p.name AS project_name,
                       p.color AS project_color,
                       a.due_date AS date_value,
                       NULL AS role_value,
                       CASE
                           WHEN LOWER(a.title) = ? THEN 100
                           WHEN LOWER(a.title) LIKE ? THEN 80
                           WHEN LOWER(COALESCE(a.description, '')) LIKE ? THEN 30
                           ELSE 0
                       END AS score_value,
                       a.updated_at AS sort_value
                  FROM action a
                  LEFT JOIN project p
                    ON p.id = a.project_id
                   AND p.company_id = a.company_id
                 WHERE a.company_id = ?
                   AND ((a.action_type = 'PERSONAL' AND a.assignee_member_id = ?)
                        OR (a.action_type = 'TEAM' AND a.team_id = ?))
                   AND (LOWER(a.title) LIKE ? OR LOWER(COALESCE(a.description, '')) LIKE ?)
                 ORDER BY score_value DESC, sort_value DESC, a.id ASC
                 LIMIT ?
                """;
        return jdbcTemplate.query(sql, this::mapItem,
                exact(keyword), prefix(keyword), like(keyword),
                scope.companyId(), scope.requesterMemberId(), scope.requesterTeamId(), like(keyword), like(keyword), limit);
    }

    private long countProject(SearchScope scope, String keyword) {
        String sql = """
                SELECT COUNT(*)
                  FROM project p
                 WHERE p.company_id = ?
                   AND p.deleted_at IS NULL
                   AND (? = TRUE OR EXISTS (
                           SELECT 1 FROM project_team pts
                            WHERE pts.project_id = p.id
                              AND pts.team_id = ?
                       ))
                   AND (LOWER(p.name) LIKE ? OR LOWER(p.tag) LIKE ? OR LOWER(COALESCE(p.description, '')) LIKE ?)
                """;
        return queryCount(sql, scope.companyId(), scope.companyAdmin(), scope.requesterTeamId(), like(keyword), like(keyword), like(keyword));
    }

    private List<SearchHit> searchProject(SearchScope scope, String keyword, int limit) {
        String sql = """
                SELECT 'PROJECT' AS result_type,
                       p.id AS id,
                       p.name AS title,
                       COALESCE(p.description, '') AS snippet,
                       p.id AS project_id,
                       p.tag AS project_tag,
                       p.name AS project_name,
                       p.color AS project_color,
                       CAST(p.updated_at AS DATE) AS date_value,
                       NULL AS role_value,
                       CASE
                           WHEN LOWER(p.tag) = ? THEN 110
                           WHEN LOWER(p.name) = ? THEN 100
                           WHEN LOWER(p.name) LIKE ? THEN 80
                           WHEN LOWER(p.tag) LIKE ? THEN 70
                           WHEN LOWER(COALESCE(p.description, '')) LIKE ? THEN 30
                           ELSE 0
                       END AS score_value,
                       p.updated_at AS sort_value
                  FROM project p
                 WHERE p.company_id = ?
                   AND p.deleted_at IS NULL
                   AND (? = TRUE OR EXISTS (
                           SELECT 1 FROM project_team pts
                            WHERE pts.project_id = p.id
                              AND pts.team_id = ?
                       ))
                   AND (LOWER(p.name) LIKE ? OR LOWER(p.tag) LIKE ? OR LOWER(COALESCE(p.description, '')) LIKE ?)
                 ORDER BY score_value DESC, sort_value DESC, p.id ASC
                 LIMIT ?
                """;
        return jdbcTemplate.query(sql, this::mapItem,
                exact(keyword), exact(keyword), prefix(keyword), prefix(keyword), like(keyword),
                scope.companyId(), scope.companyAdmin(), scope.requesterTeamId(),
                like(keyword), like(keyword), like(keyword), limit);
    }

    private long countPerson(SearchScope scope, String keyword) {
        String sql = """
                SELECT COUNT(*)
                  FROM member m
                  LEFT JOIN job_position jp
                    ON jp.id = m.job_position_id
                   AND jp.company_id = m.company_id
                 WHERE m.company_id = ?
                   AND m.deleted_at IS NULL
                   AND m.id <> ?
                   AND (LOWER(m.name) LIKE ? OR LOWER(m.role) LIKE ? OR LOWER(COALESCE(jp.name, '')) LIKE ?)
                   AND (
                       EXISTS (
                           SELECT 1
                             FROM meeting_attendee mine
                             JOIN meeting mt
                               ON mt.id = mine.meeting_id
                              AND mt.company_id = ?
                             JOIN meeting_attendee other
                               ON other.meeting_id = mine.meeting_id
                              AND other.member_id = m.id
                            WHERE mine.member_id = ?
                       )
                       OR EXISTS (
                           SELECT 1
                             FROM project p
                             JOIN project_team pt_person
                               ON pt_person.project_id = p.id
                              AND pt_person.team_id = m.team_id
                            WHERE p.company_id = ?
                              AND p.deleted_at IS NULL
                              AND (? = TRUE OR EXISTS (
                                      SELECT 1 FROM project_team pt_scope
                                       WHERE pt_scope.project_id = p.id
                                         AND pt_scope.team_id = ?
                                  ))
                       )
                   )
                """;
        return queryCount(sql,
                scope.companyId(), scope.requesterMemberId(), like(keyword), like(keyword), like(keyword),
                scope.companyId(), scope.requesterMemberId(),
                scope.companyId(), scope.companyAdmin(), scope.requesterTeamId());
    }

    private List<SearchHit> searchPerson(SearchScope scope, String keyword, int limit) {
        String sql = """
                SELECT 'PERSON' AS result_type,
                       m.id AS id,
                       m.name AS title,
                       COALESCE(jp.name, m.role) AS snippet,
                       NULL AS project_id,
                       NULL AS project_tag,
                       NULL AS project_name,
                       NULL AS project_color,
                       CAST(m.updated_at AS DATE) AS date_value,
                       m.role AS role_value,
                       CASE
                           WHEN LOWER(m.name) = ? THEN 100
                           WHEN LOWER(m.name) LIKE ? THEN 80
                           WHEN LOWER(COALESCE(jp.name, '')) LIKE ? THEN 40
                           WHEN LOWER(m.role) LIKE ? THEN 20
                           ELSE 0
                       END AS score_value,
                       m.updated_at AS sort_value
                  FROM member m
                  LEFT JOIN job_position jp
                    ON jp.id = m.job_position_id
                   AND jp.company_id = m.company_id
                 WHERE m.company_id = ?
                   AND m.deleted_at IS NULL
                   AND m.id <> ?
                   AND (LOWER(m.name) LIKE ? OR LOWER(m.role) LIKE ? OR LOWER(COALESCE(jp.name, '')) LIKE ?)
                   AND (
                       EXISTS (
                           SELECT 1
                             FROM meeting_attendee mine
                             JOIN meeting mt
                               ON mt.id = mine.meeting_id
                              AND mt.company_id = ?
                             JOIN meeting_attendee other
                               ON other.meeting_id = mine.meeting_id
                              AND other.member_id = m.id
                            WHERE mine.member_id = ?
                       )
                       OR EXISTS (
                           SELECT 1
                             FROM project p
                             JOIN project_team pt_person
                               ON pt_person.project_id = p.id
                              AND pt_person.team_id = m.team_id
                            WHERE p.company_id = ?
                              AND p.deleted_at IS NULL
                              AND (? = TRUE OR EXISTS (
                                      SELECT 1 FROM project_team pt_scope
                                       WHERE pt_scope.project_id = p.id
                                         AND pt_scope.team_id = ?
                                  ))
                       )
                   )
                 ORDER BY score_value DESC, sort_value DESC, m.id ASC
                 LIMIT ?
                """;
        return jdbcTemplate.query(sql, this::mapItem,
                exact(keyword), prefix(keyword), like(keyword), like(keyword),
                scope.companyId(), scope.requesterMemberId(), like(keyword), like(keyword), like(keyword),
                scope.companyId(), scope.requesterMemberId(),
                scope.companyId(), scope.companyAdmin(), scope.requesterTeamId(),
                limit);
    }

    private long queryCount(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
    }

    private SearchHit mapItem(ResultSet rs, int rowNum) throws SQLException {
        Project project = null;
        Long projectId = nullableLong(rs, "project_id");
        if (projectId != null) {
            project = new Project(
                    projectId,
                    rs.getString("project_tag"),
                    rs.getString("project_name"),
                    rs.getString("project_color")
            );
        }

        return new SearchHit(
                SearchType.valueOf(rs.getString("result_type")),
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("snippet"),
                project,
                readDate(rs, "date_value"),
                rs.getString("role_value"),
                rs.getDouble("score_value")
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDate readDate(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private String exact(String keyword) {
        return keyword.toLowerCase();
    }

    private String prefix(String keyword) {
        return keyword.toLowerCase() + "%";
    }

    private String like(String keyword) {
        return "%" + keyword.toLowerCase() + "%";
    }
}
