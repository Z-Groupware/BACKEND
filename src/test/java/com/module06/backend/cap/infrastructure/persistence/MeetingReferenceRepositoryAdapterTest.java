package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;

/*
 * CAP 회의 참조 어댑터의 isHost 판정(meeting.host_member_id 비교), countAttendees(CAP-13 participant
 * 이벤트의 totalCount)를 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("CAP 회의 참조 어댑터 — isHost")
class MeetingReferenceRepositoryAdapterTest {

    @Autowired
    private MeetingReferenceRepository meetingReferenceRepository;

    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    @Autowired
    private SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    @BeforeEach
    void clear() {
        springDataMeetingRepository.deleteAll();
    }

    /* host_member_id와 일치하는 사람만 isHost가 true인지 검증한다. */
    @Test
    @DisplayName("회의 담당자만 isHost가 true다")
    void isHostOnlyForHost() {
        // 담당자(host)가 3번인 회의를 저장한다.
        Long meetingId = springDataMeetingRepository.save(MeetingJpaEntity.from(Meeting.create(
                10L, 12L, null, 2L, 3L, "수동 업로드 대상 회의",
                LocalDateTime.of(2026, 8, 6, 14, 0), LocalDateTime.of(2026, 8, 6, 15, 0),
                false, null, List.of(3L)))).getId();

        assertThat(meetingReferenceRepository.isHost(meetingId, 3L)).isTrue();
        assertThat(meetingReferenceRepository.isHost(meetingId, 9L)).isFalse();
        // 존재하지 않는 회의는 false.
        assertThat(meetingReferenceRepository.isHost(999_999L, 3L)).isFalse();
    }

    /* 개설자(host)를 포함해 중복 없이 참석자 수를 세는지 검증한다(CAP-13 participant 이벤트의 totalCount). */
    @Test
    @DisplayName("전체 참석자 수를 센다(개설자 포함, 중복 제거)")
    void countsAttendeesIncludingHost() {
        // 개설자(3L) + 요청 참석자(9L, 15L). MeetingJpaEntity.from()은 meeting 행만 저장하므로
        // (실제 저장 어댑터 MeetingPersistenceAdapter가 별도로 meeting_attendee를 함께 쓴다),
        // 참석자 행은 여기서 직접 넣는다 — 회의실 예약 슬롯 FK 등 이 테스트와 무관한 경로는 타지 않는다.
        Long meetingId = springDataMeetingRepository.save(MeetingJpaEntity.from(Meeting.create(
                10L, 12L, null, 2L, 3L, "참석자 카운트 테스트 회의",
                LocalDateTime.of(2026, 8, 6, 14, 0), LocalDateTime.of(2026, 8, 6, 15, 0),
                false, null, List.of(9L, 15L)))).getId();
        for (Long memberId : List.of(3L, 9L, 15L)) {
            springDataMeetingAttendeeRepository.save(new MeetingAttendeeJpaEntity(meetingId, memberId));
        }

        assertThat(meetingReferenceRepository.countAttendees(meetingId)).isEqualTo(3);
        // 존재하지 않는 회의는 0.
        assertThat(meetingReferenceRepository.countAttendees(999_999L)).isZero();
    }

    /* meeting.project_id를 그대로 돌려주는지, 회의가 없으면 empty인지 검증한다(access-guard의 프로젝트
       멤버 판정 원천). */
    @Test
    @DisplayName("회의가 태그된 프로젝트 id를 조회한다")
    void findsProjectId() {
        Long meetingId = springDataMeetingRepository.save(MeetingJpaEntity.from(Meeting.create(
                10L, 12L, null, 2L, 3L, "프로젝트 id 조회 테스트 회의",
                LocalDateTime.of(2026, 8, 6, 14, 0), LocalDateTime.of(2026, 8, 6, 15, 0),
                false, null, List.of(3L)))).getId();

        assertThat(meetingReferenceRepository.findProjectId(meetingId)).contains(12L);
        // 존재하지 않는 회의는 empty.
        assertThat(meetingReferenceRepository.findProjectId(999_999L)).isEmpty();
    }
}
