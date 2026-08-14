package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.service.MeetingTitleProvider;

/*
 * meeting 에서 회의 제목을 읽는다.
 *
 * JdbcTemplate 을 쓰는 이유는 {@link MeetingDateJdbcProvider} 와 같다 — meeting 은 D(회의)
 * 도메인 소유이고, 같은 테이블에 JPA 매핑을 하나 더 만들면 2026-08-05 에 테스트 9건을
 * 깨뜨렸던 사고가 재발한다. 읽기 쿼리 하나로 끝낸다.
 *
 * 회사 스코프를 조건에 넣지 않는다. 호출 경로가 이미 MeetingAccessGuard 를 지난 뒤이고,
 * 이 값은 제목 하나다 — 대신 이 클래스를 관문 밖에서 부르지 않는다
 * (MeetingDateJdbcProvider 와 같은 규약).
 */
@Component
@RequiredArgsConstructor
public class MeetingTitleJdbcProvider implements MeetingTitleProvider {

    private static final String SQL = """
            SELECT m.title AS title
              FROM meeting m
             WHERE m.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> titleOf(long meetingId) {
        return jdbcTemplate.query(SQL,
                rs -> {
                    if (!rs.next()) {
                        return Optional.<String>empty();
                    }
                    return Optional.ofNullable(rs.getString("title"));
                },
                meetingId);
    }
}
