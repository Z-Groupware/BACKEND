package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.service.MeetingParticipantProvider;

/*
 * meeting_attendee ⋈ member 로 참석자 명단을 만든다.
 *
 * JdbcTemplate 을 쓰는 이유 — 두 테이블 모두 **다른 도메인 소유**다. JPA 엔티티로 매핑해
 * 연관관계를 만들면 그쪽 스키마 변경이 이쪽을 깨뜨리고, 반대로 이쪽이 그 테이블에 쓰기를
 * 할 수 있게 된다. 읽기 쿼리 하나로 끝내는 편이 경계가 분명하다.
 *
 * 소프트 삭제된 구성원(deleted_at)은 제외한다. 퇴사자가 담당자 후보로 남아 있으면
 * 액션이 그 사람에게 배정되고, 그 액션은 아무도 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MeetingParticipantJdbcProvider implements MeetingParticipantProvider {

    private static final String SQL = """
            SELECT m.id, m.name
              FROM meeting_attendee a
              JOIN member m ON m.id = a.member_id
             WHERE a.meeting_id = ?
               AND m.deleted_at IS NULL
             ORDER BY m.id
            """;

    /* 명단 밖 발화자를 가리키는 탈출구다. Python 쪽 unknown_person 과 짝이다. */
    private static final String UNKNOWN_PERSON_LABEL = "명단 외";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<AiLayerPort.Participant> participantsOf(long meetingId) {
        List<AiLayerPort.Participant> participants = new ArrayList<>(
                jdbcTemplate.query(SQL,
                        (rs, rowNum) -> new AiLayerPort.Participant(rs.getLong("id"), rs.getString("name")),
                        meetingId));

        // 탈출구는 명단이 비어 있어도 넣는다. 참석자를 한 명도 못 찾은 회의에서 목록이
        // 통째로 비면 계층이 enum 을 만들 수 없어 호출 자체가 무의미해진다.
        participants.add(new AiLayerPort.Participant(null, UNKNOWN_PERSON_LABEL));
        return List.copyOf(participants);
    }
}
