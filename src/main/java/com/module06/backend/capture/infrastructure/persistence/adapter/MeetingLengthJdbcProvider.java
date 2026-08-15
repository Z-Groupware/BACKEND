package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

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
                    LocalDateTime startedAt = localDateTime(rs.getObject("started_at", Timestamp.class));
                    LocalDateTime endedAt = localDateTime(rs.getObject("ended_at", Timestamp.class));

                    /*
                     * 둘 중 하나라도 없으면 **모르는 것**이다. 0 으로 채우지 않는다 —
                     * 0 은 "아주 짧은 회의"로 읽혀 비용 관문이 분석을 건너뛰게 만든다.
                     */
                    if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
                        return Optional.<Duration>empty();
                    }
                    return Optional.of(Duration.between(startedAt, endedAt));
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

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
