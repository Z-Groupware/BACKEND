package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.service.MeetingLengthProvider;

/*
 * meeting 에서 회의 실측 길이를 읽는다.
 *
 * JdbcTemplate 을 쓰는 이유는 {@link MeetingDateJdbcProvider} 와 같다 — meeting 은 D(회의)
 * 도메인 소유이고, 같은 테이블에 JPA 매핑을 하나 더 만들면 2026-08-05 에 테스트 9건을
 * 깨뜨렸던 사고가 재발한다.
 *
 * 뺄셈을 SQL 이 아니라 자바에서 한다. TIMESTAMPDIFF 는 방언마다 인자 순서와 지원 단위가
 * 다르고(H2 MySQL 모드가 특히 그렇다), 그 차이는 테스트에서 안 보이다가 운영에서만 터진다.
 * 시각 둘을 그대로 읽어오면 그 위험이 사라진다.
 */
@Component
@RequiredArgsConstructor
public class MeetingLengthJdbcProvider implements MeetingLengthProvider {

    private static final String LENGTH_SQL = """
            SELECT m.started_at AS started_at, m.ended_at AS ended_at
              FROM meeting m
             WHERE m.id = ?
            """;

    private static final String ONLINE_SQL = """
            SELECT m.is_online AS is_online
              FROM meeting m
             WHERE m.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Optional<Duration> actualLengthOf(long meetingId) {
        return jdbcTemplate.query(LENGTH_SQL,
                rs -> {
                    if (!rs.next()) {
                        return Optional.<Duration>empty();
                    }
                    /*
                     * 둘 중 하나라도 없으면 **모르는 것**이다. 0 으로 채우지 않는다 —
                     * 0 은 "아주 짧은 회의"로 읽혀 비용 관문이 분석을 건너뛰게 만든다.
                     */
                    return between(rs.getObject("started_at", Timestamp.class),
                            rs.getObject("ended_at", Timestamp.class));
                },
                meetingId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Boolean> isOnline(long meetingId) {
        return jdbcTemplate.query(ONLINE_SQL,
                rs -> {
                    if (!rs.next()) {
                        return Optional.<Boolean>empty();
                    }
                    return Optional.of(rs.getBoolean("is_online"));
                },
                meetingId);
    }

    /*
     * 배치 조회다. 단건 두 쿼리(LENGTH_SQL · ONLINE_SQL)를 회의마다 반복하면 목록 화면에서
     * 회의 수 × 2 쿼리가 된다 — 같은 행에서 읽히는 값이므로 한 번에 가져온다.
     *
     * 길이 판정(둘 중 하나라도 없으면 「모름」)은 단건과 **같은 규칙**을 쓴다. 여기서 따로
     * 적으면 목록과 상세가 같은 회의를 다르게 말한다.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, MeetingLength> lengthsOf(List<Long> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = meetingIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }

        String placeholders = distinct.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT m.id AS id, m.started_at AS started_at, m.ended_at AS ended_at,
                       m.is_online AS is_online
                  FROM meeting m
                 WHERE m.id IN (%s)
                """.formatted(placeholders);

        return jdbcTemplate.query(sql,
                rs -> {
                    Map<Long, MeetingLength> found = new LinkedHashMap<>();
                    while (rs.next()) {
                        found.put(rs.getLong("id"), new MeetingLength(
                                between(rs.getObject("started_at", Timestamp.class),
                                        rs.getObject("ended_at", Timestamp.class)),
                                rs.getBoolean("is_online")));
                    }
                    return found;
                },
                distinct.toArray());
    }

    /* 단건·배치가 공유하는 길이 계산이다. 둘 중 하나라도 없으면 **모르는 것**이다. */
    private Optional<Duration> between(Timestamp startedAtValue, Timestamp endedAtValue) {
        LocalDateTime startedAt = localDateTime(startedAtValue);
        LocalDateTime endedAt = localDateTime(endedAtValue);
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(startedAt, endedAt));
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
