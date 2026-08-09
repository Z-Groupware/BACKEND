package com.module06.backend.meetingroom.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meetingroom.domain.repository.MeetingAttendanceRepository;
import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingAttendeeReferenceEntity;
import com.module06.backend.meetingroom.infrastructure.persistence.repository.SpringDataMeetingAttendeeReferenceRepository;

/*
 * ROOM-02 제목 마스킹 판단에 쓰는 참석 여부 조회를 검증하는 통합 테스트다.
 *
 * 참석자 명단에 여러 구성원과 회의를 섞어 저장해
 * 요청자 본인의 참석 회의만 조회되는지 확인한다.
 */
@SpringBootTest
@Transactional
@DisplayName("ROOM-02 회의 참석 여부 영속성 어댑터")
class MeetingAttendancePersistenceAdapterTest {

    /* 테스트 참석자 데이터를 저장하고 초기화할 읽기 전용 참조 저장소다. */
    @Autowired
    private SpringDataMeetingAttendeeReferenceRepository springDataMeetingAttendeeReferenceRepository;

    /* application 계층이 실제로 사용하는 참석 여부 도메인 저장소 계약이다. */
    @Autowired
    private MeetingAttendanceRepository meetingAttendanceRepository;

    /*
     * 각 테스트가 서로의 데이터에 영향을 주지 않도록 참석자 데이터를 초기화한다.
     */
    @BeforeEach
    void clearAttendees() {
        /* 이전 테스트에서 저장한 참석자 행을 모두 삭제한다. */
        springDataMeetingAttendeeReferenceRepository.deleteAll();
    }

    /*
     * 요청자가 참석자로 등록된 회의의 식별자만 조회되는지 검증한다.
     */
    @Test
    @DisplayName("요청자가 참석자인 회의 식별자만 조회한다")
    void findsOnlyMeetingsAttendedByRequester() {
        /* 요청자가 91번 회의에만 참석하고, 94번 회의에는 다른 구성원이 참석한 상황을 만든다. */
        springDataMeetingAttendeeReferenceRepository.save(new MeetingAttendeeReferenceEntity(91L, 3L));
        springDataMeetingAttendeeReferenceRepository.save(new MeetingAttendeeReferenceEntity(94L, 7L));
        springDataMeetingAttendeeReferenceRepository.save(new MeetingAttendeeReferenceEntity(95L, 3L));

        /* 현황판에 등장한 회의 두 건에 대해 참석 여부를 조회한다. */
        Set<Long> result = meetingAttendanceRepository.findAttendedMeetingIds(3L, List.of(91L, 94L));

        /* 조회 대상에 없는 95번은 빠지고, 다른 구성원의 94번도 포함되지 않아야 한다. */
        assertThat(result).containsExactly(91L);
    }

    /*
     * 참석 여부를 확인할 회의가 없을 때 조회 없이 빈 집합을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("확인할 회의가 없거나 요청자를 특정할 수 없으면 빈 집합을 반환한다")
    void returnsEmptySetWhenNothingToCheck() {
        /* 참석자 행이 있어도 확인 대상이 비어 있으면 빈 집합이어야 한다. */
        springDataMeetingAttendeeReferenceRepository.save(new MeetingAttendeeReferenceEntity(91L, 3L));

        /* 회의 목록이 비어 있는 경우와 구성원 식별자가 없는 경우를 각각 조회한다. */
        assertThat(meetingAttendanceRepository.findAttendedMeetingIds(3L, List.of())).isEmpty();
        assertThat(meetingAttendanceRepository.findAttendedMeetingIds(null, List.of(91L))).isEmpty();
    }
}
