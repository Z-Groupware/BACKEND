package com.module06.backend.capture.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.MeetingAccessPort;

/*
 * meeting 에서 회사 스코프만 확인한다.
 *
 * JdbcTemplate 을 쓰는 이유 — meeting 은 D(회의) 도메인 소유다. 엔티티로 매핑하면 같은
 * 테이블에 매핑이 셋이 되고, 그게 2026-08-05 에 테스트 9건을 깨뜨린 사고다
 * (MeetingJpaEntity ↔ MeetingReferenceEntity 중복 매핑 → 스키마 생성이 환경에 따라 갈렸다).
 * 읽기 한 줄로 끝내면 그 위험이 없고 경계도 분명하다
 * ({@link MeetingParticipantJdbcProvider} 와 같은 방식).
 *
 * SELECT 1 + LIMIT 1 인 이유: 존재 여부만 필요하다. COUNT(*) 는 조건에 맞는 행을 다 세고,
 * EXISTS 는 첫 행에서 멈춘다 — 여기서는 항상 0 또는 1 행이라 차이가 크지 않지만,
 * "존재 확인"이라는 의도가 쿼리에 그대로 보이는 편이 낫다.
 */
@Component
@RequiredArgsConstructor
public class MeetingAccessJdbcAdapter implements MeetingAccessPort {

    private static final String SQL = """
            SELECT 1
              FROM meeting
             WHERE id = ?
               AND company_id = ?
             LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public boolean existsInCompany(long companyId, long meetingId) {
        return !jdbcTemplate.queryForList(SQL, Integer.class, meetingId, companyId).isEmpty();
    }
}
