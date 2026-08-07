package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.service.MeetingHostProvider;

/*
 * meeting 에서 회의 담당자를 읽는다(RVW-05 의 403 판정).
 *
 * JdbcTemplate 을 쓰는 이유는 {@link MeetingDateJdbcProvider} 와 같다 — meeting 은 D(회의)
 * 도메인 소유이고, 같은 테이블에 JPA 매핑을 하나 더 만들면 2026-08-05 에 테스트 9건을
 * 깨뜨렸던 사고가 재발한다.
 *
 * 회사 스코프를 조건에 넣지 않는다. 호출 경로가 이미 MeetingAccessGuard 를 지난 뒤이고,
 * 이 값은 담당자 id 하나다 — 대신 **이 클래스를 관문 밖에서 부르지 않는다**
 * (MeetingDateJdbcProvider 와 같은 규약).
 */
@Component
@RequiredArgsConstructor
public class MeetingHostJdbcProvider implements MeetingHostProvider {

    private static final String SQL = """
            SELECT m.host_member_id AS host_member_id
              FROM meeting m
             WHERE m.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> hostMemberIdOf(long meetingId) {
        return jdbcTemplate.query(SQL,
                rs -> {
                    if (!rs.next()) {
                        return Optional.<Long>empty();
                    }
                    long hostMemberId = rs.getLong("host_member_id");
                    // NOT NULL 컬럼이지만 getLong 은 NULL 을 0 으로 준다. 0 을 담당자로 넘기면
                    // 아무와도 일치하지 않는 값으로 조용히 403 이 되므로 빈 값으로 구분한다.
                    return rs.wasNull() ? Optional.<Long>empty() : Optional.of(hostMemberId);
                },
                meetingId);
    }
}
