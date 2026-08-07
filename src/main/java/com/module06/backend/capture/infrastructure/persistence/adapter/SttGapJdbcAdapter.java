package com.module06.backend.capture.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.SttGapRepository;

/*
 * stt_gap(V5.5) 조회 어댑터다. 지금은 분배 확정의 관문(RVW-05)만 쓴다.
 *
 * resolved_at 이 NULL 인 것만 센다 — 사람이 다시 듣고 확인하면 그 시각이 찍히고, 그때부터는
 * 구멍이 남아 있어도 분배를 막지 않는다. "구멍이 있다"가 아니라 **"아무도 안 본 구멍이 있다"**가
 * 막는 조건이다(V5.5 주석).
 *
 * IX_STT_GAP_MEETING_RESOLVED(meeting_id, resolved_at)가 이 조회를 받는다.
 */
@Component
@RequiredArgsConstructor
public class SttGapJdbcAdapter implements SttGapRepository {

    private static final String COUNT_UNRESOLVED_SQL = """
            SELECT COUNT(*)
              FROM stt_gap g
             WHERE g.meeting_id = ?
               AND g.resolved_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public int countUnresolved(long meetingId) {
        Integer count = jdbcTemplate.queryForObject(COUNT_UNRESOLVED_SQL, Integer.class, meetingId);
        return count != null ? count : 0;
    }
}
