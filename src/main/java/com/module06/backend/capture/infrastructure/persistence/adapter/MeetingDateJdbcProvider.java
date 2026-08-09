package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.service.MeetingDateProvider;

/*
 * meeting 에서 회의 날짜를 읽는다.
 *
 * JdbcTemplate 을 쓰는 이유 — meeting 은 D(회의) 도메인 소유다. JPA 엔티티로 매핑하면 같은
 * 테이블에 매핑이 여럿 생기는데, 그게 정확히 2026-08-05 에 테스트 9건을 깨뜨린 사고다
 * (MeetingAccessPort 주석 참조). 읽기 쿼리 하나로 끝낸다.
 *
 * COALESCE(started_at, start_at) 인 이유 — 실제로 열린 날짜가 기준이어야 한다. 예정일과
 * 실제일이 다른 회의에서 예정일을 쓰면 "다음 주까지"가 하루씩 밀린 날짜로 계산된다.
 * started_at 은 NULL 일 수 있고(회의를 시작 처리하지 않은 경로) start_at 은 NOT NULL 이라
 * 폴백이 항상 있다.
 *
 * 회사 스코프를 조건에 넣지 않는다. 이 값은 회의 날짜 하나이고, 호출 경로가 이미
 * MeetingAccessGuard 를 지난 뒤다 — 여기서 다시 회사를 요구하면 orchestrator 가 쓰지 않는
 * 인자를 들고 다니게 된다. 대신 **이 클래스를 관문 밖에서 부르지 않는다.**
 */
@Component
@RequiredArgsConstructor
public class MeetingDateJdbcProvider implements MeetingDateProvider {

    private static final String SQL = """
            SELECT DATE(COALESCE(m.started_at, m.start_at)) AS meeting_date
              FROM meeting m
             WHERE m.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> meetingDateOf(long meetingId) {
        return jdbcTemplate.query(SQL,
                        rs -> {
                            if (!rs.next()) {
                                return Optional.<LocalDate>empty();
                            }
                            Date value = rs.getObject("meeting_date", Date.class);
                            return Optional.ofNullable(value).map(Date::toLocalDate);
                        },
                        meetingId);
    }
}
