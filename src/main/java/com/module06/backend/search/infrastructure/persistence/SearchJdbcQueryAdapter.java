package com.module06.backend.search.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

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
        List<String> tags = normalizedTags(scope);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                  FROM meeting m
                  JOIN meeting_attendee mas
                    ON mas.meeting_id = m.id
                   AND mas.member_id = ?
                  LEFT JOIN project p
                    ON p.id = m.project_id
                   AND p.company_id = m.company_id
                  LEFT JOIN meeting_summary ms
                    ON ms.meeting_id = m.id
                   AND ms.company_id = m.company_id
                 WHERE m.company_id = ?
                   AND (LOWER(m.title) LIKE ? ESCAPE '\' OR LOWER(COALESCE(ms.overview, '')) LIKE ? ESCAPE '\')
                """);
        args.add(scope.requesterMemberId());
        args.add(scope.companyId());
        args.add(like(keyword));
        args.add(like(keyword));
        appendProjectTagFilter(sql, args, "p", tags);
        appendDateTimeFilter(sql, args, "m.start_at", scope);
        return queryCount(sql.toString(), args);
    }

    private List<SearchHit> searchMeeting(SearchScope scope, String keyword, int limit) {
        List<String> tags = normalizedTags(scope);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
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
                           WHEN LOWER(m.title) LIKE ? ESCAPE '\' THEN 80
                           WHEN LOWER(COALESCE(ms.overview, '')) LIKE ? ESCAPE '\' THEN 30
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
                   AND (LOWER(m.title) LIKE ? ESCAPE '\' OR LOWER(COALESCE(ms.overview, '')) LIKE ? ESCAPE '\')
                """);
        args.add(exact(keyword));
        args.add(prefix(keyword));
        args.add(like(keyword));
        args.add(scope.requesterMemberId());
        args.add(scope.companyId());
        args.add(like(keyword));
        args.add(like(keyword));
        appendProjectTagFilter(sql, args, "p", tags);
        appendDateTimeFilter(sql, args, "m.start_at", scope);
        sql.append("""
                 ORDER BY score_value DESC, sort_value DESC, m.id ASC
                 LIMIT ?
                """);
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapItem, args.toArray());
    }

    private long countAction(SearchScope scope, String keyword) {
        List<String> tags = normalizedTags(scope);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                  FROM action a
                  LEFT JOIN project p
                    ON p.id = a.project_id
                   AND p.company_id = a.company_id
                 WHERE a.company_id = ?
                   AND ((a.action_type = 'PERSONAL' AND a.assignee_member_id = ?)
                        OR (a.action_type = 'TEAM' AND a.team_id = ?))
                   AND (LOWER(a.title) LIKE ? ESCAPE '\' OR LOWER(COALESCE(a.description, '')) LIKE ? ESCAPE '\')
                """);
        args.add(scope.companyId());
        args.add(scope.requesterMemberId());
        args.add(scope.requesterTeamId());
        args.add(like(keyword));
        args.add(like(keyword));
        appendProjectTagFilter(sql, args, "p", tags);
        appendDateFilter(sql, args, "a.due_date", scope);
        return queryCount(sql.toString(), args);
    }

    private List<SearchHit> searchAction(SearchScope scope, String keyword, int limit) {
        List<String> tags = normalizedTags(scope);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
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
                           WHEN LOWER(a.title) LIKE ? ESCAPE '\' THEN 80
                           WHEN LOWER(COALESCE(a.description, '')) LIKE ? ESCAPE '\' THEN 30
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
                   AND (LOWER(a.title) LIKE ? ESCAPE '\' OR LOWER(COALESCE(a.description, '')) LIKE ? ESCAPE '\')
                """);
        args.add(exact(keyword));
        args.add(prefix(keyword));
        args.add(like(keyword));
        args.add(scope.companyId());
        args.add(scope.requesterMemberId());
        args.add(scope.requesterTeamId());
        args.add(like(keyword));
        args.add(like(keyword));
        appendProjectTagFilter(sql, args, "p", tags);
        appendDateFilter(sql, args, "a.due_date", scope);
        sql.append("""
                 ORDER BY score_value DESC, sort_value DESC, a.id ASC
                 LIMIT ?
                """);
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapItem, args.toArray());
    }

    private long countProject(SearchScope scope, String keyword) {
        List<String> tags = normalizedTags(scope);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                  FROM project p
                 WHERE p.company_id = ?
                   AND p.deleted_at IS NULL
                   AND (? = TRUE OR EXISTS (
                           SELECT 1 FROM project_team pts
                            WHERE pts.project_id = p.id
                              AND pts.team_id = ?
                       ))
                   AND (LOWER(p.name) LIKE ? ESCAPE '\' OR LOWER(p.tag) LIKE ? ESCAPE '\' OR LOWER(COALESCE(p.description, '')) LIKE ? ESCAPE '\')
                """);
        args.add(scope.companyId());
        args.add(scope.companyAdmin());
        args.add(scope.requesterTeamId());
        args.add(like(keyword));
        args.add(like(keyword));
        args.add(like(keyword));
        appendProjectTagFilter(sql, args, "p", tags);
        /*
         * SR-2 기간 필터는 회의/액션의 업무 날짜 조건이다. 프로젝트 자체의 생성/수정/마감일에는 적용하지 않는다.
         */
        return queryCount(sql.toString(), args);
    }

    private List<SearchHit> searchProject(SearchScope scope, String keyword, int limit) {
        List<String> tags = normalizedTags(scope);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
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
                           WHEN LOWER(p.name) LIKE ? ESCAPE '\' THEN 80
                           WHEN LOWER(p.tag) LIKE ? ESCAPE '\' THEN 70
                           WHEN LOWER(COALESCE(p.description, '')) LIKE ? ESCAPE '\' THEN 30
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
                   AND (LOWER(p.name) LIKE ? ESCAPE '\' OR LOWER(p.tag) LIKE ? ESCAPE '\' OR LOWER(COALESCE(p.description, '')) LIKE ? ESCAPE '\')
                """);
        args.add(exact(keyword));
        args.add(exact(keyword));
        args.add(prefix(keyword));
        args.add(prefix(keyword));
        args.add(like(keyword));
        args.add(scope.companyId());
        args.add(scope.companyAdmin());
        args.add(scope.requesterTeamId());
        args.add(like(keyword));
        args.add(like(keyword));
        args.add(like(keyword));
        appendProjectTagFilter(sql, args, "p", tags);
        /*
         * SR-2 기간 필터는 회의/액션의 업무 날짜 조건이다. 프로젝트 자체의 생성/수정/마감일에는 적용하지 않는다.
         */
        sql.append("""
                 ORDER BY score_value DESC, sort_value DESC, p.id ASC
                 LIMIT ?
                """);
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapItem, args.toArray());
    }

    private long countPerson(SearchScope scope, String keyword) {
        if (!normalizedTags(scope).isEmpty()) {
            /*
             * 현재 스키마에서 PERSON은 project.tag와 직접 연결되지 않는다. 브리프의 태그 필터 범위는 프로젝트 태그이므로
             * tags 조건이 있으면 사람 결과를 count/result 모두에서 제외한다.
             */
            return 0L;
        }
        String sql = """
                SELECT COUNT(*)
                  FROM member m
                  LEFT JOIN job_position jp
                    ON jp.id = m.job_position_id
                   AND jp.company_id = m.company_id
                 WHERE m.company_id = ?
                   AND m.deleted_at IS NULL
                   AND m.id <> ?
                   AND (LOWER(m.name) LIKE ? ESCAPE '\' OR LOWER(m.role) LIKE ? ESCAPE '\' OR LOWER(COALESCE(jp.name, '')) LIKE ? ESCAPE '\')
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
        if (!normalizedTags(scope).isEmpty()) {
            /*
             * 현재 스키마에서 PERSON은 project.tag와 직접 연결되지 않는다. 브리프의 태그 필터 범위는 프로젝트 태그이므로
             * tags 조건이 있으면 사람 결과를 count/result 모두에서 제외한다.
             */
            return List.of();
        }
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
                       jp.name AS role_value,
                       CASE
                           WHEN LOWER(m.name) = ? THEN 100
                           WHEN LOWER(m.name) LIKE ? ESCAPE '\' THEN 80
                           WHEN LOWER(COALESCE(jp.name, '')) LIKE ? ESCAPE '\' THEN 40
                           WHEN LOWER(m.role) LIKE ? ESCAPE '\' THEN 20
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
                   AND (LOWER(m.name) LIKE ? ESCAPE '\' OR LOWER(m.role) LIKE ? ESCAPE '\' OR LOWER(COALESCE(jp.name, '')) LIKE ? ESCAPE '\')
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

    private long queryCount(String sql, List<Object> args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private List<String> normalizedTags(SearchScope scope) {
        if (scope.tags() == null || scope.tags().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : scope.tags()) {
            if (tag != null && !tag.isBlank()) {
                normalized.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(normalized);
    }

    private void appendProjectTagFilter(StringBuilder sql, List<Object> args, String projectAlias, List<String> tags) {
        if (tags.isEmpty()) {
            return;
        }
        sql.append("   AND LOWER(")
                .append(projectAlias)
                .append(".tag) IN (")
                .append("?,".repeat(tags.size()), 0, tags.size() * 2 - 1)
                .append(")\n");
        args.addAll(tags);
    }

    private void appendDateTimeFilter(StringBuilder sql, List<Object> args, String column, SearchScope scope) {
        if (scope.from() != null) {
            sql.append("   AND ").append(column).append(" >= ?\n");
            args.add(scope.from().atStartOfDay());
        }
        if (scope.to() != null) {
            sql.append("   AND ").append(column).append(" < ?\n");
            args.add(scope.to().plusDays(1).atStartOfDay());
        }
    }

    private void appendDateFilter(StringBuilder sql, List<Object> args, String column, SearchScope scope) {
        if (scope.from() != null) {
            sql.append("   AND ").append(column).append(" >= ?\n");
            args.add(scope.from());
        }
        if (scope.to() != null) {
            sql.append("   AND ").append(column).append(" <= ?\n");
            args.add(scope.to());
        }
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
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return null;
    }

    private String exact(String keyword) {
        return keyword.toLowerCase(Locale.ROOT);
    }

    private String prefix(String keyword) {
        return escapeLike(keyword) + "%";
    }

    private String like(String keyword) {
        return "%" + escapeLike(keyword) + "%";
    }

    /**
     * LIKE 패턴 메타문자(%, _)와 이스케이프 문자(\)를 무력화하여 사용자 입력을 리터럴로 취급한다.
     * 이 결과를 쓰는 모든 SQL LIKE 절에는 {@code ESCAPE '\'}가 함께 있어야 한다.
     */
    private String escapeLike(String keyword) {
        return keyword.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
