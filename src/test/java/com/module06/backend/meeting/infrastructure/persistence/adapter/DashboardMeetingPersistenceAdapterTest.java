package com.module06.backend.meeting.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meeting.domain.model.DashboardMeetingScope;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository.DashboardMeetingCandidate;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository.DashboardMeetingCriteria;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingReservationSlotRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingTopicRepository;

/*
 * MEET-17 대시보드 최근 회의 조회의 스코프별 필터·취소 제외·정렬을 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("MEET-17 대시보드 최근 회의 영속성 어댑터")
class DashboardMeetingPersistenceAdapterTest {

    /* 애플리케이션 계층이 사용하는 실제 대시보드 조회 저장소 계약이다. */
    @Autowired
    private DashboardMeetingRepository dashboardMeetingRepository;

    /* 테스트 회의 행을 저장하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    /* 테스트 참석자 행을 저장하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* 다른 통합 테스트가 남긴 안건 행을 먼저 지우기 위한 기술 저장소다. */
    @Autowired
    private SpringDataMeetingTopicRepository springDataMeetingTopicRepository;

    /* 다른 통합 테스트가 커밋한 예약 슬롯을 회의보다 먼저 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingReservationSlotRepository springDataMeetingReservationSlotRepository;

    /* 테스트마다 자식 슬롯·참석자·안건을 먼저 지운 뒤 회의 데이터를 초기화한다. */
    @BeforeEach
    void clearMeetingData() {
        springDataMeetingReservationSlotRepository.deleteAll();
        springDataMeetingAttendeeRepository.deleteAll();
        springDataMeetingTopicRepository.deleteAll();
        springDataMeetingRepository.deleteAll();
    }

    /* scope=OWNER는 host 본인 회의만 남기고 취소 회의와 타 회사 회의를 제외해야 한다. */
    @Test
    @DisplayName("scope=OWNER는 host 본인의 취소되지 않은 회의만 최신순으로 반환한다")
    void findsOwnerScopedMeetings() {
        MeetingJpaEntity hosted = springDataMeetingRepository.save(
                meeting(10L, 5L, 100L, 3L, null, "내가 개설한 회의", LocalDateTime.of(2026, 8, 10, 14, 0))
        );
        springDataMeetingRepository.save(
                canceledMeeting(10L, 5L, 100L, 3L, null, "취소된 내 회의", LocalDateTime.of(2026, 8, 11, 14, 0))
        );
        springDataMeetingRepository.save(
                meeting(10L, 5L, 100L, 99L, null, "다른 사람이 개설한 회의", LocalDateTime.of(2026, 8, 12, 14, 0))
        );
        springDataMeetingRepository.save(
                meeting(20L, 5L, 100L, 3L, null, "다른 회사에서 내가 개설한 회의", LocalDateTime.of(2026, 8, 13, 14, 0))
        );

        List<DashboardMeetingCandidate> candidates = dashboardMeetingRepository.findDashboardMeetings(
                new DashboardMeetingCriteria(10L, DashboardMeetingScope.OWNER, 3L, null, 5)
        );

        assertThat(candidates)
                .extracting(DashboardMeetingCandidate::meetingId)
                .containsExactly(hosted.getId());
    }

    /* MEET-18 온라인 회의는 startAt이 없어 이 카드 정렬·표시와 맞지 않으므로 스코프와 무관하게 제외돼야 한다. */
    @Test
    @DisplayName("MEET-18 온라인 회의는 스코프와 무관하게 제외한다")
    void excludesOnlineMeetings() {
        MeetingJpaEntity hosted = springDataMeetingRepository.save(
                meeting(10L, 5L, 100L, 3L, null, "내가 개설한 회의", LocalDateTime.of(2026, 8, 10, 14, 0))
        );
        springDataMeetingRepository.save(onlineMeeting(10L, 5L, 100L, 3L, "내가 개설한 온라인 회의"));

        List<DashboardMeetingCandidate> candidates = dashboardMeetingRepository.findDashboardMeetings(
                new DashboardMeetingCriteria(10L, DashboardMeetingScope.OWNER, 3L, null, 5)
        );

        assertThat(candidates)
                .extracting(DashboardMeetingCandidate::meetingId)
                .containsExactly(hosted.getId());
    }

    /* scope=TEAM은 팀 소속이면서 상위 팀 액션이 연결된 회의만 남기고 나머지는 제외해야 한다. */
    @Test
    @DisplayName("scope=TEAM은 같은 팀이면서 related_action_id가 있는 회의만 반환한다")
    void findsTeamScopedMeetings() {
        MeetingJpaEntity teamMeetingWithAction = springDataMeetingRepository.save(
                meeting(10L, 5L, 100L, 7L, 200L, "팀 액션에서 열린 회의", LocalDateTime.of(2026, 8, 10, 14, 0))
        );
        springDataMeetingRepository.save(
                meeting(10L, 5L, 100L, 7L, null, "팀장이 직접 개설한 회의", LocalDateTime.of(2026, 8, 11, 14, 0))
        );
        springDataMeetingRepository.save(
                meeting(10L, 5L, 999L, 7L, 201L, "다른 팀 회의", LocalDateTime.of(2026, 8, 12, 14, 0))
        );

        List<DashboardMeetingCandidate> candidates = dashboardMeetingRepository.findDashboardMeetings(
                new DashboardMeetingCriteria(10L, DashboardMeetingScope.TEAM, 7L, 100L, 5)
        );

        assertThat(candidates)
                .extracting(DashboardMeetingCandidate::meetingId)
                .containsExactly(teamMeetingWithAction.getId());
    }

    /* scope=ME는 참석자로 등록된 회의만 반환하며 host 여부와 무관해야 한다. */
    @Test
    @DisplayName("scope=ME는 참석자로 등록된 회의를 최신순으로 상한만큼 반환한다")
    void findsMeScopedMeetingsOrderedAndLimited() {
        MeetingJpaEntity latest = springDataMeetingRepository.save(
                meeting(10L, 5L, 100L, 99L, null, "최근 참석 회의", LocalDateTime.of(2026, 8, 12, 14, 0))
        );
        saveAttendees(latest.getId(), 99L, 3L);

        MeetingJpaEntity older = springDataMeetingRepository.save(
                meeting(10L, 5L, 100L, 3L, null, "내가 개설하고 참석한 회의", LocalDateTime.of(2026, 8, 11, 14, 0))
        );
        saveAttendees(older.getId(), 3L);

        MeetingJpaEntity notAttending = springDataMeetingRepository.save(
                meeting(10L, 5L, 100L, 99L, null, "참석하지 않는 회의", LocalDateTime.of(2026, 8, 13, 14, 0))
        );
        saveAttendees(notAttending.getId(), 99L);

        List<DashboardMeetingCandidate> candidates = dashboardMeetingRepository.findDashboardMeetings(
                new DashboardMeetingCriteria(10L, DashboardMeetingScope.ME, 3L, null, 1)
        );

        /* limit=1이라 두 참석 회의 중 startAt이 더 늦은 회의만 반환돼야 한다. */
        assertThat(candidates)
                .extracting(DashboardMeetingCandidate::meetingId)
                .containsExactly(latest.getId());
    }

    /* 지정한 조건으로 필수 컬럼을 모두 가진 예약 상태 회의 엔티티를 만든다. */
    private MeetingJpaEntity meeting(
            Long companyId,
            Long projectId,
            Long teamId,
            Long hostMemberId,
            Long relatedActionId,
            String title,
            LocalDateTime startAt
    ) {
        Meeting created = Meeting.create(
                companyId,
                projectId,
                teamId,
                2L,
                hostMemberId,
                title,
                startAt,
                startAt.plusHours(1),
                false,
                relatedActionId,
                List.of(hostMemberId)
        );
        return MeetingJpaEntity.from(created);
    }

    /* MEET-18 온라인 회의 — meetingRoomId·startAt·endAt 없이 isOnline=true로 만든다. */
    private MeetingJpaEntity onlineMeeting(
            Long companyId,
            Long projectId,
            Long teamId,
            Long hostMemberId,
            String title
    ) {
        Meeting created = Meeting.createOnline(
                companyId,
                projectId,
                teamId,
                hostMemberId,
                title,
                true,
                null,
                List.of()
        );
        return MeetingJpaEntity.from(created);
    }

    /* 지정한 조건으로 예약 상태 회의를 만든 뒤 시작 한 시간 전에 취소된 상태로 전이시킨다. */
    private MeetingJpaEntity canceledMeeting(
            Long companyId,
            Long projectId,
            Long teamId,
            Long hostMemberId,
            Long relatedActionId,
            String title,
            LocalDateTime startAt
    ) {
        Meeting scheduled = Meeting.create(
                companyId,
                projectId,
                teamId,
                2L,
                hostMemberId,
                title,
                startAt,
                startAt.plusHours(1),
                false,
                relatedActionId,
                List.of(hostMemberId)
        );
        return MeetingJpaEntity.from(scheduled.cancel(startAt.minusHours(1)));
    }

    /* 회의와 여러 구성원 식별자로 참석자 행을 일괄 저장한다. */
    private void saveAttendees(Long meetingId, Long... memberIds) {
        List<MeetingAttendeeJpaEntity> attendees = java.util.Arrays.stream(memberIds)
                .map(memberId -> new MeetingAttendeeJpaEntity(meetingId, memberId))
                .toList();
        springDataMeetingAttendeeRepository.saveAll(attendees);
    }
}
