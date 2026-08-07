package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.GateSignals;
import com.module06.backend.capture.domain.model.RejectReason;

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
 * <h2>발화 조인에 회의 조건을 함께 건다</h2>
 * {@code tc.meeting_id = a.source_meeting_id} 가 붙어 있다. id 만으로 조인하면 액션에 박힌
 * evidence_transcript_id 가 **다른 회의(다른 회사)의 발화**를 가리켜도 그 원문이 화면에
 * 인용된다. 값을 넣는 쪽(RVW-03)에서 이미 막지만, 여기서 한 번 더 막는 이유는 그 컬럼에
 * 값을 쓰는 경로가 앞으로 늘어날 수 있어서다 — 한 곳이 빠지면 그 경로만 조용히 뚫린다(#100).
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
     *
     * <h2>반려 사유는 review_log 의 마지막 행에서 온다</h2>
     * action 에는 사유 컬럼이 없다 — 사유는 액션의 현재 상태가 아니라 **사람이 그때 내린
     * 판단**이고, 라벨로 남아야 하는 값이다(V5.9). 마지막 행을 보는 이유는 사람이 판정을
     * 두 번 할 수 있기 때문이다(반려했다가 다시 확인). 마지막이 CONFIRM 이면 사유가 NULL 이고,
     * 그게 곧 "지금은 반려 상태가 아니다"라는 뜻이라 화면이 라벨을 지운다.
     *
     * 상관 서브쿼리를 쓰는 이유 — JOIN 하면 판정 이력이 여러 개인 액션이 여러 행으로 불어난다
     * (tuple 조인과 같은 문제이고, 이쪽은 UNIQUE 로 막을 수 없다. 이력은 원래 여럿이다).
     * IX_REVIEW_LOG_TARGET(target_type, target_id)이 이 조회를 받는다.
     */
    private static final String SQL = """
            SELECT a.id                          AS action_id,
                   a.assignee_member_id          AS assignee_member_id,
                   am.name                       AS assignee_name,
                   a.assignee_source             AS assignee_source,
                   a.title                       AS title,
                   a.description                 AS detail,
                   a.due_date                    AS due_date,
                   a.due_date_defaulted          AS due_date_defaulted,
                   a.is_manual                   AS is_manual,
                   a.review_status               AS review_status,
                   a.evidence_transcript_id      AS evidence_transcript_id,
                   (SELECT rl.reject_reason
                      FROM review_log rl
                     WHERE rl.target_type = 'ACTION'
                       AND rl.target_id = a.id
                     ORDER BY rl.id DESC
                     LIMIT 1)                    AS reject_reason,
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
                                            AND tc.meeting_id = a.source_meeting_id
              LEFT JOIN member sm ON sm.id = tc.speaker_member_id
             WHERE a.source_meeting_id = ?
               AND a.company_id = ?
               AND (? IS NULL OR a.review_status = ?)
             ORDER BY a.assignee_member_id, a.id
            """;

    /*
     * 판정 대상 하나(RVW-02). tuple 에서 AI 원본을 함께 읽는다.
     *
     * action 의 값과 tuple 의 값을 **둘 다** 읽는 것이 요점이다 — action 은 사람이 고친 뒤
     * 덮여 있을 수 있어서 "AI 가 무엇을 냈는가"의 답이 아니다. 라벨은 그 둘의 차이다.
     */
    private static final String ONE_SQL = """
            SELECT a.id                          AS action_id,
                   a.assignee_member_id          AS assignee_member_id,
                   a.due_date                    AS due_date,
                   a.title                       AS title,
                   a.is_manual                   AS is_manual,
                   a.review_status               AS review_status,
                   a.evidence_transcript_id      AS evidence_transcript_id,
                   tc.content                    AS evidence_content,
                   t.topic                       AS topic,
                   t.title                       AS ai_title,
                   t.assignee_candidate_member_id AS ai_assignee_member_id,
                   t.assignee_source             AS ai_assignee_source,
                   t.due_date                    AS ai_due_date,
                   t.model_name                  AS ai_model_name,
                   t.prompt_version              AS ai_prompt_version
              FROM action a
              LEFT JOIN meeting_assignment_tuple t ON t.action_id = a.id
              LEFT JOIN transcript_chunk tc ON tc.id = a.evidence_transcript_id
                                            AND tc.meeting_id = a.source_meeting_id
             WHERE a.id = ?
               AND a.source_meeting_id = ?
               AND a.company_id = ?
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
                rs.getBoolean("due_date_defaulted"),
                rs.getString("topic"),
                rs.getBoolean("is_manual"),
                rs.getString("review_status"),
                rejectReasonOf(rs),
                evidenceOf(rs),
                signalsOf(rs),
                nullableBoolean(rs, "gate_auto_confirmed"));
    }

    /*
     * 판정 대상 하나를 읽는다(RVW-02).
     *
     * 목록과 쿼리를 나눈 이유 — 여기서만 필요한 것이 있다. **AI 가 원래 낸 값**(tuple 의
     * title·담당자·기한·모델·프롬프트 버전)은 라벨의 llm_output 이고 화면은 쓰지 않는다.
     * 목록에 실으면 액션 수십 건마다 쓰이지 않는 컬럼 다섯이 따라다닌다.
     *
     * 회의를 조건에 함께 넣는다. actionId 만으로 찾으면 **다른 회의의 액션 id 를 넣어 남의
     * 액션을 고칠 수 있다** — 관문(MeetingAccessGuard)은 회의까지만 본다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewTarget> findOne(long companyId, long meetingId, long actionId) {
        return jdbcTemplate.query(ONE_SQL,
                rs -> {
                    if (!rs.next()) {
                        return Optional.<ReviewTarget>empty();
                    }
                    return Optional.of(toReviewTarget(rs));
                },
                actionId, meetingId, companyId);
    }

    private ReviewTarget toReviewTarget(ResultSet rs) throws SQLException {
        return new ReviewTarget(
                rs.getLong("action_id"),
                nullableLong(rs, "assignee_member_id"),
                rs.getObject("due_date", java.time.LocalDate.class),
                rs.getString("title"),
                rs.getBoolean("is_manual"),
                rs.getString("review_status"),
                nullableLong(rs, "evidence_transcript_id"),
                rs.getString("evidence_content"),
                rs.getString("topic"),
                aiValueOf(rs));
    }

    /*
     * AI 원본. tuple 이 없으면(수동 추가 액션 · RVW-03) null 이다.
     *
     * tuple 의 title 로 존재를 판정한다 — NOT NULL 컬럼이라 조인이 붙었을 때만 값이 있다.
     * 빈 AiValue 를 만들면 "AI 가 빈 값을 냈다"로 읽히는데, 그건 AI 를 부른 적이 없는 것과
     * 다른 상태다. 그 구분이 라벨의 is_manual 과 짝을 이룬다.
     */
    private AiValue aiValueOf(ResultSet rs) throws SQLException {
        String aiTitle = rs.getString("ai_title");
        if (aiTitle == null) {
            return null;
        }
        return new AiValue(
                aiTitle,
                nullableLong(rs, "ai_assignee_member_id"),
                AssigneeSource.fromNullable(rs.getString("ai_assignee_source")),
                rs.getObject("ai_due_date", java.time.LocalDate.class),
                rs.getString("ai_model_name"),
                rs.getString("ai_prompt_version"));
    }

    /* 알 수 없는 값이 오면 던진다 — 사유 코드가 늘었는데 이쪽이 모르는 상태를 감추지 않는다. */
    private RejectReason rejectReasonOf(ResultSet rs) throws SQLException {
        String value = rs.getString("reject_reason");
        return value == null ? null : RejectReason.valueOf(value);
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
