package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.List;

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

    /* 배치 조회는 id 를 돌려받아야 한다 — 어느 것이 남의 회사 것이었는지 호출자가 가려낸다. */
    private static final String BATCH_SQL_PREFIX = """
            SELECT id
              FROM meeting
             WHERE company_id = ?
               AND id IN (""";

    private static final int CHUNK_SIZE = 200;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public boolean existsInCompany(long companyId, long meetingId) {
        return !jdbcTemplate.queryForList(SQL, Integer.class, meetingId, companyId).isEmpty();
    }

    /*
     * IN 절을 id 개수만큼 만들어 붙인다. 청킹은 **호출자가 아니라 여기가** 한다 —
     * MeetingActionQueryPort 가 정한 것과 같은 규칙이다(배치 크기는 계약이 아니다).
     *
     * 200 은 그쪽 구현이 쓰는 값과 같게 맞췄다. 플레이스홀더가 많아지면 MySQL 파서와
     * 프리페어드 스테이트먼트 캐시가 같이 부담을 받는데, 마이페이지가 한 번에 보내는 회의
     * 수는 그 한참 아래라 실제로 쪼개질 일은 드물다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> filterInCompany(long companyId, List<Long> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }
        // 중복 id 가 섞여 오면 IN 절만 길어진다. 결과도 집합이라 여기서 접는다.
        List<Long> distinct = meetingIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }

        List<Long> found = new ArrayList<>();
        for (int from = 0; from < distinct.size(); from += CHUNK_SIZE) {
            List<Long> chunk = distinct.subList(from, Math.min(from + CHUNK_SIZE, distinct.size()));
            String placeholders = String.join(", ", java.util.Collections.nCopies(chunk.size(), "?"));

            Object[] args = new Object[chunk.size() + 1];
            args[0] = companyId;
            for (int i = 0; i < chunk.size(); i++) {
                args[i + 1] = chunk.get(i);
            }

            found.addAll(jdbcTemplate.queryForList(
                    BATCH_SQL_PREFIX + placeholders + ")", Long.class, args));
        }
        return found;
    }
}
