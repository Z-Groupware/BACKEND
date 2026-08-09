package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.sql.ResultSet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.DistributedActionProbe;

/*
 * action 에 이 회의의 분배 이력이 있는지 읽는다.
 *
 * JdbcTemplate 을 쓰는 이유는 MeetingDateJdbcProvider 와 같다 — action 은 C(과제) 도메인
 * 소유다. JPA 엔티티로 매핑하면 그쪽 스키마 변경이 이쪽을 깨뜨리고, 반대로 이쪽이 그 테이블에
 * 쓰기를 할 수 있게 된다. 여기서 필요한 것은 존재 여부 하나이므로 읽기 쿼리로 끝낸다.
 *
 * LIMIT 1 로 끊는다. 건수를 세어야 할 이유가 없고, 회의 하나에 액션이 수십 건일 수 있다.
 */
@Component
@RequiredArgsConstructor
public class DistributedActionJdbcProbe implements DistributedActionProbe {

    private static final String SQL = """
            SELECT 1
              FROM action a
             WHERE a.source_meeting_id = ?
               AND a.company_id = ?
               AND a.is_manual = FALSE
             LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public boolean existsForMeeting(long companyId, long meetingId) {
        ResultSetExtractor<Boolean> hasRow = ResultSet::next;
        return Boolean.TRUE.equals(jdbcTemplate.query(SQL, hasRow, meetingId, companyId));
    }
}
