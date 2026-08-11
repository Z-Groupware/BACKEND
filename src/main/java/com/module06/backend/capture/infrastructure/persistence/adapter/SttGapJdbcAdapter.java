package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@Component
@RequiredArgsConstructor
public class SttGapJdbcAdapter implements SttGapRepository {

    private static final String COUNT_UNRESOLVED_SQL = """
            SELECT COUNT(*)
              FROM stt_gap g
             WHERE g.meeting_id = ?
               AND g.resolved_at IS NULL
            """;

    private static final String FIND_BY_MEETING_SQL = """
            SELECT g.start_offset_ms, g.end_offset_ms, g.reason
              FROM stt_gap g
             WHERE g.meeting_id = ?
             ORDER BY g.start_offset_ms ASC, g.id ASC
            """;

    /*
     * 그 블록의 받아쓰기 구멍만 지운다. reason 을 조건에 넣는 이유 — 같은 블록 구간에
     * 조립 구멍(ASSEMBLY_GAP)이 따로 기록됐을 수 있고, 그건 cap 이 판정한 다른 사실이다.
     * 받아쓰기가 성공했다고 조립 구멍까지 지우면 남의 판정을 덮는다.
     */
    private static final String DELETE_STT_GAP_SQL = """
            DELETE FROM stt_gap
             WHERE meeting_id = ?
               AND stt_block_seq = ?
               AND reason = 'STT_FAILED'
            """;

    private static final String INSERT_STT_GAP_SQL = """
            INSERT INTO stt_gap (meeting_id, start_offset_ms, end_offset_ms, reason, stt_block_seq)
            VALUES (?, ?, ?, 'STT_FAILED', ?)
            """;

    private static final String DELETE_RECORDING_GAP_SQL = """
            DELETE FROM stt_gap
             WHERE meeting_id = ?
               AND start_offset_ms = ?
               AND end_offset_ms = ?
               AND reason = ?
            """;

    private static final String INSERT_RECORDING_GAP_SQL = """
            INSERT INTO stt_gap (meeting_id, start_offset_ms, end_offset_ms, reason)
            VALUES (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public int countUnresolved(long meetingId) {
        Integer count = jdbcTemplate.queryForObject(COUNT_UNRESOLVED_SQL, Integer.class, meetingId);
        return count != null ? count : 0;
    }

    /*
     * mentioned_names · keywords 를 **읽지 않는다.**
     *
     * 컬럼은 있지만 채우는 쪽이 없다 — 이 두 값은 그 구간의 자막 **본문**에서 뽑는 것이고,
     * 우리 자막 읽기 포트는 rms·오프셋만 투영한다(CaptionRepository). 빈 JSON 을 읽어
     * 빈 목록으로 내려주는 것과 결과가 같으므로 조회 자체를 줄인다. 채우는 쪽이 생기면
     * 이 SELECT 에 두 컬럼을 더한다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<GapView> findByMeeting(long meetingId) {
        return jdbcTemplate.query(FIND_BY_MEETING_SQL,
                (rs, rowNum) -> new GapView(
                        rs.getInt("start_offset_ms"),
                        rs.getInt("end_offset_ms"),
                        rs.getString("reason"),
                        List.of(),
                        List.of()),
                meetingId);
    }

    /*
     * 지우고 넣는다 — 한 트랜잭션이다.
     *
     * UNIQUE 제약으로 대신할 수 없다. (meeting_id, stt_block_seq) 에 유일 제약을 걸면 같은
     * 블록의 조립 구멍과 받아쓰기 구멍이 공존할 수 없게 되는데, 그 둘은 **다른 사실**이다.
     * 그래서 reason 까지 조건에 넣은 삭제 + 삽입으로 그 블록의 받아쓰기 구멍만 갈아 끼운다.
     */
    @Override
    @Transactional
    public void replaceSttFailureGap(long meetingId, int blockSeq, int startOffsetMs, int endOffsetMs) {
        jdbcTemplate.update(DELETE_STT_GAP_SQL, meetingId, blockSeq);
        jdbcTemplate.update(INSERT_STT_GAP_SQL, meetingId, startOffsetMs, endOffsetMs, blockSeq);
    }

    /*
     * 녹음 쪽 구멍은 **구간과 사유로** 갈아 끼운다. 블록 순번이 없어 그것으로 식별할 수 없다
     * (stt_block_seq 는 NULL 로 들어간다 — V5.5 의 "UPLOAD_MISSING 이면 NULL").
     */
    @Override
    @Transactional
    public void replaceRecordingGap(long meetingId, int startOffsetMs, int endOffsetMs, String reason) {
        jdbcTemplate.update(DELETE_RECORDING_GAP_SQL, meetingId, startOffsetMs, endOffsetMs, reason);
        jdbcTemplate.update(INSERT_RECORDING_GAP_SQL, meetingId, startOffsetMs, endOffsetMs, reason);
        log.warn("녹음 구멍 기록 — meetingId={} 구간={}~{}ms 사유={}",
                meetingId, startOffsetMs, endOffsetMs, reason);
    }

    @Override
    @Transactional
    public void clearSttFailureGap(long meetingId, int blockSeq) {
        int deleted = jdbcTemplate.update(DELETE_STT_GAP_SQL, meetingId, blockSeq);
        if (deleted > 0) {
            // 재처리가 성공해 구멍이 사라진 순간이다. 조용히 지나가면 분배가 왜 열렸는지 남지 않는다.
            log.info("받아쓰기 구멍 해소 — meetingId={} blockSeq={} 삭제={}건", meetingId, blockSeq, deleted);
        }
    }
}
