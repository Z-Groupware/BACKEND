package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.service.MeetingCompanyProvider;

/* meeting 엔티티를 중복 매핑하지 않고 자동 분석에 필요한 회사 식별자만 읽는다. */
@Component
@RequiredArgsConstructor
public class MeetingCompanyJdbcProvider implements MeetingCompanyProvider {

    private static final String SQL = """
            SELECT m.company_id AS company_id,
                   m.status AS status
              FROM meeting m
             WHERE m.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Optional<AutomaticAnalysisTarget> findAutomaticAnalysisTarget(long meetingId) {
        return jdbcTemplate.query(SQL,
                rs -> rs.next()
                        ? Optional.of(new AutomaticAnalysisTarget(
                                rs.getLong("company_id"), "DONE".equals(rs.getString("status"))))
                        : Optional.empty(),
                meetingId);
    }
}
