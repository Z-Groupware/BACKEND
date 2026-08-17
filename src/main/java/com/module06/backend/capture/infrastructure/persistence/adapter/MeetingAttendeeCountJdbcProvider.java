package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.OptionalInt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.service.MeetingAttendeeCountProvider;

/*
 * meeting_attendee 에서 참석자 수를 센다.
 *
 * JdbcTemplate 을 쓰는 이유는 {@link MeetingDateJdbcProvider} 와 같다 — meeting_attendee 는
 * D(회의) 도메인 소유이고, 같은 테이블에 JPA 매핑을 하나 더 만드는 것이 2026-08-05 에 테스트
 * 9건을 깨뜨린 사고다. 이미 meeting·meetingroom·cap 세 도메인이 각자 매핑을 들고 있다.
 * 읽기 쿼리 하나로 끝낸다.
 *
 * 회사 스코프를 조건에 넣지 않는 것도 같은 판단이다. 돌려주는 값이 **개수 하나**라 내용이
 * 새지 않고, 부르는 경로(STT 제출)는 이미 회의를 특정한 뒤다.
 */
@Component
@RequiredArgsConstructor
public class MeetingAttendeeCountJdbcProvider implements MeetingAttendeeCountProvider {

    private static final String SQL = """
            SELECT COUNT(*) AS attendee_count
              FROM meeting_attendee ma
             WHERE ma.meeting_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public OptionalInt attendeeCountOf(long meetingId) {
        Integer count = jdbcTemplate.query(SQL,
                rs -> rs.next() ? rs.getInt("attendee_count") : null,
                meetingId);

        // 0 은 비어 있는 것으로 답한다(포트 주석). COUNT 는 행이 없어도 0 을 주므로
        // "명단이 없다"와 "0명"이 여기서는 같은 모양으로 온다 — 나눌 근거가 없다.
        return count == null || count == 0 ? OptionalInt.empty() : OptionalInt.of(count);
    }
}
