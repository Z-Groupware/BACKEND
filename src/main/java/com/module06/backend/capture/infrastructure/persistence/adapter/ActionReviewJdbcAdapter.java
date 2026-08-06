package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.GateSignals;

/*
 * RVW-01 검토 조회의 읽기 어댑터다.
 *
 * <h2>JdbcTemplate 을 쓰는 이유</h2>
 * 네 테이블 중 셋이 **다른 도메인 소유**다(action=C · member=조직 · transcript_chunk=공용).
 * JPA 엔티티로 매핑해 연관관계를 만들면 그쪽 스키마 변경이 이쪽을 깨뜨리고, 반대로 이쪽이
 * 그 테이블에 쓰기를 할 수 있게 된다. 읽기 쿼리 하나로 끝내는 편이 경계가 분명하다
 * (MeetingParticipantJdbcProvider 와 같은 판단이다).
 *
 * 화면 하나에 액션 수십 건이 뜨는데 엔티티로 풀면 근거 발화·화자 이름에서 N+1 이 난다.
 * 조인 한 번으로 끝낸다.
 *
 * <h2>LEFT JOIN 인 이유</h2>
 * 넷 다 없을 수 있다 —
 *   meeting_assignment_tuple  사람이 직접 추가한 액션(RVW-03)은 tuple 이 없다
 *   member(담당자)            담당자 미정이거나 명단 밖이다
 *   transcript_chunk          근거 발화가 없다(수동 추가)
 *   member(화자)              L1 이 화자 판정을 포기했다 — 정상 동작이다
 * INNER 로 걸면 이 액션들이 검토 화면에서 통째로 사라진다. **검토에서 빠지는 것이 가장
 * 나쁜 실패다** — 사람이 볼 기회 자체가 없어진다.
 *
 * <h2>액션당 한 행인 것은 DB 가 보장한다</h2>
 * tuple 조인이 액션을 여러 행으로 돌려주면 검토 화면이 같은 액션을 두 번 보여주고 검토
 * 대상 건수도 두 번 센다. 그래서 action_id 에 UNIQUE 를 걸었다(V5.15) — 여기서 GROUP BY 나
 * 상관 서브쿼리로 한 행을 골라내지 않는 이유다. 고르는 쪽으로 하면 어느 tuple 이 진짜인지를
 * 조회가 추측하게 되는데, 그 상태 자체가 분배 버그이므로 INSERT 에서 막는 편이 맞다.
 */
@Component
@RequiredArgsConstructor
public class ActionReviewJdbcAdapter implements ActionReviewQueryPort {

    /*
     * 게이트 신호를 action.gate_signals(JSON)가 아니라 meeting_assignment_tuple 의 컬럼에서
     * 읽는다. 같은 값이 두 곳에 있지만 이쪽이 **A 소유**이고 컬럼으로 펴져 있어, 나중에
     * "어느 조건에서 떨어졌나"를 집계할 때 JSON 을 파싱하지 않아도 된다(V5.14 주석).
     *
     * 정렬은 담당자 → 액션 id. 화면이 사람별로 묶어 보여주므로(actionsByPerson) 담당자가
     * 1차 키여야 그룹핑이 한 번에 끝난다. 담당자 미정(NULL)은 MySQL 오름차순에서 맨 앞으로
     * 오는데, 그건 "누구 것인지 모르는 액션"이라 먼저 보이는 편이 맞다.
     */
    private static final String SQL = """
            SELECT a.id                          AS action_id,
                   a.assignee_member_id          AS assignee_member_id,
                   am.name                       AS assignee_name,
                   a.assignee_source             AS assignee_source,
                   a.title                       AS title,
                   a.description                 AS detail,
                   a.due_date                    AS due_date,
                   a.is_manual                   AS is_manual,
                   a.review_status               AS review_status,
                   a.evidence_transcript_id      AS evidence_transcript_id,
                   t.topic                       AS topic,
                   t.gate_auto_confirmed         AS gate_auto_confirmed,
                   t.gate_has_evidence           AS gate_has_evidence,
                   t.gate_assignee_in_roster     AS gate_assignee_in_roster,
                   t.gate_assignee_source_ok     AS gate_assignee_source_ok,
                   t.gate_views_agree            AS gate_views_agree,
                   tc.content                    AS evidence_content,
                   tc.offset_ms                  AS evidence_offset_ms,
                   sm.name                       AS speaker_name
              FROM action a
              LEFT JOIN meeting_assignment_tuple t ON t.action_id = a.id
              LEFT JOIN member am ON am.id = a.assignee_member_id
              LEFT JOIN transcript_chunk tc ON tc.id = a.evidence_transcript_id
              LEFT JOIN member sm ON sm.id = tc.speaker_member_id
             WHERE a.source_meeting_id = ?
               AND a.company_id = ?
               AND (? IS NULL OR a.review_status = ?)
             ORDER BY a.assignee_member_id, a.id
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<ReviewAction> findByMeeting(long companyId, long meetingId, String reviewStatus) {
        // company_id 를 조건에 **함께** 넣는다. meetingId 만으로 조회하면 다른 회사 회의의
        // 액션과 근거 발화가 나간다 — 메타데이터가 아니라 회의 내용이다.
        return jdbcTemplate.query(SQL, this::toReviewAction,
                meetingId, companyId, reviewStatus, reviewStatus);
    }

    private ReviewAction toReviewAction(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewAction(
                rs.getLong("action_id"),
                nullableLong(rs, "assignee_member_id"),
                rs.getString("assignee_name"),
                AssigneeSource.fromNullable(rs.getString("assignee_source")),
                rs.getString("title"),
                rs.getString("detail"),
                rs.getObject("due_date", java.time.LocalDate.class),
                rs.getString("topic"),
                rs.getBoolean("is_manual"),
                rs.getString("review_status"),
                evidenceOf(rs),
                signalsOf(rs),
                nullableBoolean(rs, "gate_auto_confirmed"));
    }

    /*
     * 근거 발화가 없으면 null 을 준다. 빈 Evidence 를 만들면 화면이 "근거는 있는데 내용이
     * 비었다"로 읽고, 그건 사람이 원문을 찾아 헤매게 만든다.
     */
    private Evidence evidenceOf(ResultSet rs) throws SQLException {
        Long transcriptId = nullableLong(rs, "evidence_transcript_id");
        if (transcriptId == null) {
            return null;
        }
        return new Evidence(
                transcriptId,
                // L1 이 화자 판정을 포기했으면 null 이다. 모르는 이름을 지어내지 않는다.
                rs.getString("speaker_name"),
                rs.getString("evidence_content"),
                nullableInt(rs, "evidence_offset_ms"));
    }

    /*
     * 게이트를 지나지 않은 액션(수동 추가)은 null 이다. false 로 채우면 "게이트가 떨어뜨렸다"와
     * 구분되지 않고, 화면이 수동 추가 건을 AI 가 의심한 것처럼 보여준다.
     *
     * 판정 여부는 gate_auto_confirmed 로 가른다 — 신호 넷은 그 값이 있을 때만 함께 채워진다.
     */
    private GateSignals signalsOf(ResultSet rs) throws SQLException {
        if (nullableBoolean(rs, "gate_auto_confirmed") == null) {
            return null;
        }
        return new GateSignals(
                rs.getBoolean("gate_has_evidence"),
                rs.getBoolean("gate_assignee_in_roster"),
                rs.getBoolean("gate_assignee_source_ok"),
                rs.getBoolean("gate_views_agree"));
    }

    /*
     * ResultSet 의 원시형 게터는 NULL 을 0·false 로 돌려준다. 그 값을 그대로 쓰면 담당자
     * 미정이 memberId=0 으로, 미판정이 false 로 둔갑한다 — wasNull() 로 확인해야 한다.
     */
    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
