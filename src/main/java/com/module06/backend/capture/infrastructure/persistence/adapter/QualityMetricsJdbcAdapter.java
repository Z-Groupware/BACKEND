package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.QualityMetricsRepository;

/*
 * QLTY-02 지표의 원재료를 세는 어댑터다.
 *
 * <h2>JdbcTemplate 을 쓰는 이유</h2>
 * 표본(gold set)·판정(review_log)·게이트(meeting_assignment_tuple) 셋을 가로질러 센다. 엔티티로
 * 풀면 표본 회의 수만큼 N+1 이 나고, 무엇보다 **여기서 필요한 것은 행이 아니라 개수**다
 * (ActionReviewJdbcAdapter 와 같은 판단이다).
 *
 * <h2>판정은 액션마다 마지막 것만 센다</h2>
 * review_log 는 이력이다 — 사람이 반려했다가 다시 확인할 수 있다. 전부 세면 한 액션이 여러 번
 * 채점되어 **판정을 많이 바꾼 액션일수록 지표에 크게 반영된다.** 그래서 target 별 최대 id 만 본다.
 *
 * <h2>비율을 여기서 내지 않는다</h2>
 * "무엇을 TP 로 세는가"가 이 지표의 전부인데, 그 판단이 SQL 문자열에 들어가면 나중에 지표가
 * 이상할 때 근거를 코드에서 찾을 수 없다. 개수만 주고 뜻은 서비스가 갖는다.
 */
@Component
@RequiredArgsConstructor
public class QualityMetricsJdbcAdapter implements QualityMetricsRepository {

    /* 표본 = gold set 이 동결한 회의들. 버전이 여럿이어도 회의는 하나로 센다. */
    private static final String GOLD_SET_MEETINGS_SQL = """
            SELECT DISTINCT meeting_id
              FROM quality_gold_set
             WHERE company_id = ?
            """;

    /*
     * 액션마다 **마지막 판정**만 남기고 센다.
     *
     * is_manual 로 갈라야 한다 — 사람이 직접 추가한 액션(RVW-03)은 AI 가 만든 것이 아니라서
     * precision 의 분모에 들어가면 안 되고, 오히려 **AI 가 놓친 것**이라 recall 의 분모다.
     */
    private static final String DECISION_TALLY_SQL = """
            SELECT rl.is_manual                          AS is_manual,
                   rl.decision                           AS decision,
                   COUNT(*)                              AS cnt
              FROM review_log rl
             WHERE rl.company_id = ?
               AND rl.target_type = 'ACTION'
               AND rl.meeting_id IN (%s)
               AND rl.id = (SELECT MAX(last.id)
                              FROM review_log last
                             WHERE last.target_type = 'ACTION'
                               AND last.target_id = rl.target_id)
             GROUP BY rl.is_manual, rl.decision
            """;

    /* 게이트 성적. 자동 확정한 것 중 사람이 고치거나 반려한 수를 함께 센다. */
    private static final String GATE_TALLY_SQL = """
            SELECT COUNT(*)                                                        AS tuple_count,
                   SUM(CASE WHEN t.gate_auto_confirmed = TRUE THEN 1 ELSE 0 END)   AS auto_confirmed,
                   SUM(CASE WHEN t.gate_auto_confirmed = TRUE
                             AND rl.decision IS NOT NULL
                             AND rl.decision <> 'CONFIRM' THEN 1 ELSE 0 END)       AS auto_confirmed_wrong
              FROM meeting_assignment_tuple t
              LEFT JOIN review_log rl
                     ON rl.target_type = 'ACTION'
                    AND rl.target_id = t.action_id
                    AND rl.id = (SELECT MAX(last.id)
                                   FROM review_log last
                                  WHERE last.target_type = 'ACTION'
                                    AND last.target_id = t.action_id)
             WHERE t.company_id = ?
               AND t.meeting_id IN (%s)
            """;

    /*
     * 채점 대상이 어느 모델·프롬프트의 출력인가.
     *
     * 최신 하나를 본다. 버전이 섞인 표본은 지표를 비교할 수 없게 만드는데, **그 사실을 여기서
     * 감추지 않고 화면에 값 하나로 보여주는 것**이 지금 할 수 있는 최선이다 — 섞였는지까지
     * 답하려면 응답 모양이 바뀐다(후속).
     */
    private static final String VERSION_SQL = """
            SELECT rl.model_name, rl.prompt_version
              FROM review_log rl
             WHERE rl.company_id = ?
               AND rl.target_type = 'ACTION'
               AND rl.meeting_id IN (%s)
               AND rl.model_name IS NOT NULL
             ORDER BY rl.id DESC
             LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public MetricsTally tally(long companyId) {
        List<Long> meetingIds = jdbcTemplate.queryForList(GOLD_SET_MEETINGS_SQL, Long.class, companyId);
        if (meetingIds.isEmpty()) {
            /*
             * 표본이 없다. **0 으로 채운 결과를 준다** — 여기서 예외를 올리면 "아직 정답지를
             * 안 만들었다"가 오류로 보인다. 비율은 서비스가 null 로 만든다(못 잰다는 뜻).
             */
            return new MetricsTally(0, 0, 0, 0, 0, 0, 0, 0, null, null);
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(meetingIds.size(), "?"));
        Object[] scopedArgs = argsOf(companyId, meetingIds);

        DecisionTally decisions = decisionTally(placeholders, scopedArgs);
        GateTally gate = gateTally(placeholders, scopedArgs);
        String[] version = versionOf(placeholders, scopedArgs);

        return new MetricsTally(
                meetingIds.size(),
                decisions.aiValid + decisions.aiRejected + decisions.manualAdded,
                decisions.aiValid,
                decisions.aiRejected,
                decisions.manualAdded,
                gate.autoConfirmed,
                gate.autoConfirmedWrong,
                gate.tupleCount,
                version[0],
                version[1]);
    }

    private DecisionTally decisionTally(String placeholders, Object[] args) {
        DecisionTally tally = new DecisionTally();
        jdbcTemplate.query(DECISION_TALLY_SQL.formatted(placeholders), rs -> {
            boolean manual = rs.getBoolean("is_manual");
            String decision = rs.getString("decision");
            int count = rs.getInt("cnt");

            if (manual) {
                // 사람이 직접 넣은 액션이다. AI 가 만든 것이 아니라 **놓친 것**이라 FN 이다.
                tally.manualAdded += count;
            } else if ("REJECT".equals(decision)) {
                tally.aiRejected += count;
            } else {
                // CONFIRM·MODIFY 둘 다 "그 일이 있다"는 판정은 맞은 것이다.
                tally.aiValid += count;
            }
        }, args);
        return tally;
    }

    private GateTally gateTally(String placeholders, Object[] args) {
        return jdbcTemplate.query(GATE_TALLY_SQL.formatted(placeholders), rs -> {
            GateTally tally = new GateTally();
            if (rs.next()) {
                tally.tupleCount = rs.getInt("tuple_count");
                tally.autoConfirmed = rs.getInt("auto_confirmed");
                tally.autoConfirmedWrong = rs.getInt("auto_confirmed_wrong");
            }
            return tally;
        }, args);
    }

    private String[] versionOf(String placeholders, Object[] args) {
        return jdbcTemplate.query(VERSION_SQL.formatted(placeholders), rs -> {
            if (!rs.next()) {
                return new String[] {null, null};
            }
            return new String[] {rs.getString("model_name"), rs.getString("prompt_version")};
        }, args);
    }

    /* companyId 뒤에 회의 id 를 이어 붙인다 — 모든 쿼리가 같은 인자 순서를 쓴다. */
    private Object[] argsOf(long companyId, List<Long> meetingIds) {
        Object[] args = new Object[meetingIds.size() + 1];
        args[0] = companyId;
        for (int i = 0; i < meetingIds.size(); i++) {
            args[i + 1] = meetingIds.get(i);
        }
        return args;
    }

    private static final class DecisionTally {
        private int aiValid;
        private int aiRejected;
        private int manualAdded;
    }

    private static final class GateTally {
        private int tupleCount;
        private int autoConfirmed;
        private int autoConfirmedWrong;
    }
}
