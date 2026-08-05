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

import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingTopicType;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingTopicJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingReservationSlotRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingTopicRepository;

/*
 * RESULT-01과 E 연동 기반의 회사 격리·정렬·배치 참석자 조회를 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("RESULT-01 회의 조회 영속성 어댑터")
class MeetingQueryPersistenceAdapterTest {

    /* 애플리케이션 계층이 사용하는 실제 회의 조회 저장소 계약이다. */
    @Autowired
    private MeetingQueryRepository meetingQueryRepository;

    /* 테스트 회의 행을 저장하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    /* 테스트 참석자 행을 저장하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* 테스트 회의 안건 행을 저장하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingTopicRepository springDataMeetingTopicRepository;

    /* 다른 통합 테스트가 커밋한 예약 슬롯을 회의보다 먼저 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingReservationSlotRepository springDataMeetingReservationSlotRepository;

    /* 테스트마다 자식 슬롯·참석자·안건을 먼저 지운 뒤 회의 데이터를 초기화한다. */
    @BeforeEach
    void clearMeetingData() {
        /* 회의 참조 가능성이 있는 슬롯과 참석자와 안건을 먼저 삭제한다. */
        springDataMeetingReservationSlotRepository.deleteAll();
        springDataMeetingAttendeeRepository.deleteAll();
        springDataMeetingTopicRepository.deleteAll();

        /* 자식 데이터가 없는 상태에서 회의 기본 행을 삭제한다. */
        springDataMeetingRepository.deleteAll();
    }

    /* 회사와 식별자가 모두 일치할 때만 회의와 참석자가 조회되는지 검증한다. */
    @Test
    @DisplayName("같은 회사의 회의와 참석자만 단건 조회한다")
    void findsMeetingOnlyInsideCompanyScope() {
        /* 회사 10의 회의와 세 명의 참석자를 저장한다. */
        MeetingJpaEntity meeting = springDataMeetingRepository.save(
                meeting(10L, 12L, "A커머스 온보딩", LocalDateTime.of(2026, 8, 6, 14, 0))
        );
        saveAttendees(meeting.getId(), 3L, 7L, 11L);

        /* 같은 회사에서 조회하면 회의와 전체 참석자 식별자가 반환돼야 한다. */
        assertThat(meetingQueryRepository.findMeeting(10L, meeting.getId()))
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    /* 회의 표시 정보와 개설자·참석자 값이 저장 데이터와 일치해야 한다. */
                    assertThat(snapshot.title()).isEqualTo("A커머스 온보딩");
                    assertThat(snapshot.hostMemberId()).isEqualTo(3L);
                    assertThat(snapshot.attendeeMemberIds()).containsExactly(3L, 7L, 11L);
                });

        /* 같은 식별자라도 다른 회사로 조회하면 존재 여부가 드러나지 않아야 한다. */
        assertThat(meetingQueryRepository.findMeeting(20L, meeting.getId())).isEmpty();
    }

    /* 프로젝트 회의가 시작 시각 순으로 안정적으로 조회되는지 검증한다. */
    @Test
    @DisplayName("프로젝트 회의를 시작 시각과 식별자 오름차순으로 조회한다")
    void findsProjectMeetingsInTimelineOrder() {
        /* 같은 프로젝트의 회의를 역순으로 저장하고 다른 프로젝트·회사의 회의도 섞는다. */
        MeetingJpaEntity later = springDataMeetingRepository.save(
                meeting(10L, 12L, "두 번째 회의", LocalDateTime.of(2026, 8, 7, 14, 0))
        );
        MeetingJpaEntity earlier = springDataMeetingRepository.save(
                meeting(10L, 12L, "첫 번째 회의", LocalDateTime.of(2026, 8, 6, 14, 0))
        );
        springDataMeetingRepository.save(
                meeting(10L, 99L, "다른 프로젝트", LocalDateTime.of(2026, 8, 5, 14, 0))
        );
        springDataMeetingRepository.save(
                meeting(20L, 12L, "다른 회사", LocalDateTime.of(2026, 8, 5, 14, 0))
        );

        /* 회사 10의 12번 프로젝트 회의 타임라인을 조회한다. */
        List<MeetingQueryRepository.ProjectMeetingSnapshot> result =
                meetingQueryRepository.findProjectMeetingsOrdered(10L, 12L);

        /* 다른 범위 회의는 제외되고 이른 회의부터 반환돼야 한다. */
        assertThat(result)
                .extracting(MeetingQueryRepository.ProjectMeetingSnapshot::meetingId)
                .containsExactly(earlier.getId(), later.getId());
    }

    /* 여러 회의 참석자 조회가 회사 범위를 지키면서 한 번에 수행되는지 검증한다. */
    @Test
    @DisplayName("배치 참석자 조회에서 다른 회사 회의를 제외한다")
    void findsBatchAttendeesOnlyInsideCompanyScope() {
        /* 요청 회사의 회의 두 건과 다른 회사 회의 한 건을 저장한다. */
        MeetingJpaEntity first = springDataMeetingRepository.save(
                meeting(10L, 12L, "첫 번째 회의", LocalDateTime.of(2026, 8, 6, 14, 0))
        );
        MeetingJpaEntity second = springDataMeetingRepository.save(
                meeting(10L, 12L, "두 번째 회의", LocalDateTime.of(2026, 8, 7, 14, 0))
        );
        MeetingJpaEntity otherCompany = springDataMeetingRepository.save(
                meeting(20L, 12L, "다른 회사 회의", LocalDateTime.of(2026, 8, 8, 14, 0))
        );

        /* 각 회의에 참석자 행을 저장한다. */
        saveAttendees(first.getId(), 3L, 7L);
        saveAttendees(second.getId(), 3L, 11L);
        saveAttendees(otherCompany.getId(), 30L, 70L);

        /* 세 회의 식별자를 모두 넘기되 회사 10 범위로 배치 조회한다. */
        List<MeetingQueryRepository.MeetingAttendeeReference> result =
                meetingQueryRepository.findMeetingAttendees(
                        10L,
                        List.of(first.getId(), second.getId(), otherCompany.getId())
                );

        /* 회사 10의 두 회의 참석자만 반환되고 다른 회사 구성원은 제외돼야 한다. */
        assertThat(result)
                .extracting(MeetingQueryRepository.MeetingAttendeeReference::meetingId)
                .containsOnly(first.getId(), second.getId());
        assertThat(result)
                .extracting(MeetingQueryRepository.MeetingAttendeeReference::memberId)
                .containsExactlyInAnyOrder(3L, 7L, 3L, 11L);
    }

    /* 여러 회의 안건 조회가 회사 범위와 회의별 표시 순서를 지키는지 검증한다. */
    @Test
    @DisplayName("배치 안건 조회에서 다른 회사 회의를 제외하고 표시 순서를 유지한다")
    void findsBatchTopicsOnlyInsideCompanyScopeInDisplayOrder() {
        /* 요청 회사의 회의 한 건과 다른 회사 회의 한 건을 저장한다. */
        MeetingJpaEntity meeting = springDataMeetingRepository.save(
                meeting(10L, 12L, "프로젝트 회의", LocalDateTime.of(2026, 8, 6, 14, 0))
        );
        MeetingJpaEntity otherCompany = springDataMeetingRepository.save(
                meeting(20L, 12L, "다른 회사 회의", LocalDateTime.of(2026, 8, 7, 14, 0))
        );

        /* 같은 회의 안건을 표시 순서와 저장 순서가 다르게 준비한다. */
        springDataMeetingTopicRepository.saveAll(List.of(
                new MeetingTopicJpaEntity(meeting.getId(), null, MeetingTopicType.SUB, "세부 범위", 1),
                new MeetingTopicJpaEntity(meeting.getId(), null, MeetingTopicType.MAIN, "출시 범위", 0),
                new MeetingTopicJpaEntity(otherCompany.getId(), null, MeetingTopicType.MAIN, "비공개 안건", 0)
        ));

        /* 두 회사 회의 식별자를 함께 넘기되 회사 10의 범위로 안건을 조회한다. */
        List<MeetingQueryRepository.MeetingTopicSnapshot> result = meetingQueryRepository.findMeetingTopics(
                10L,
                List.of(meeting.getId(), otherCompany.getId())
        );

        /* 타 회사 안건은 제외되고 요청 회사의 안건만 sortOrder 순서로 반환돼야 한다. */
        assertThat(result)
                .extracting(MeetingQueryRepository.MeetingTopicSnapshot::content)
                .containsExactly("출시 범위", "세부 범위");
        assertThat(result)
                .extracting(MeetingQueryRepository.MeetingTopicSnapshot::type)
                .containsExactly(MeetingTopicType.MAIN, MeetingTopicType.SUB);
    }

    /* MEET-03 후보 조회가 참석자·회사·종료 시각·정렬·limit 규칙을 지키는지 검증한다. */
    @Test
    @DisplayName("내 예정 회의를 회사 범위와 시작 시각 순서로 제한 조회한다")
    void findsUpcomingMeetingsForAttendeeInCompanyScope() {
        /* 조회 기준 시각을 2026년 8월 6일 오전 9시로 고정한다. */
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 9, 0);

        /* 같은 회사의 가까운 회의와 이후 회의를 저장한다. */
        MeetingJpaEntity first = springDataMeetingRepository.save(
                meeting(10L, 12L, "가까운 회의", LocalDateTime.of(2026, 8, 6, 9, 5))
        );
        MeetingJpaEntity second = springDataMeetingRepository.save(
                meeting(10L, 13L, "다음 회의", LocalDateTime.of(2026, 8, 6, 10, 0))
        );

        /* 이미 종료된 회의, 다른 회사 회의, 요청자가 참석하지 않은 회의도 함께 저장한다. */
        MeetingJpaEntity expired = springDataMeetingRepository.save(
                meeting(10L, 12L, "지난 회의", LocalDateTime.of(2026, 8, 6, 7, 0))
        );
        MeetingJpaEntity otherCompany = springDataMeetingRepository.save(
                meeting(20L, 12L, "다른 회사 회의", LocalDateTime.of(2026, 8, 6, 9, 10))
        );
        MeetingJpaEntity nonAttendee = springDataMeetingRepository.save(
                meeting(10L, 12L, "비참석 회의", LocalDateTime.of(2026, 8, 6, 9, 15))
        );

        /* 요청자 7번은 앞의 네 회의에만 참석자로 연결하고 회의별 전체 인원수도 다르게 만든다. */
        saveAttendees(first.getId(), 3L, 7L, 11L, 15L);
        saveAttendees(second.getId(), 3L, 7L);
        saveAttendees(expired.getId(), 3L, 7L);
        saveAttendees(otherCompany.getId(), 30L, 7L);
        saveAttendees(nonAttendee.getId(), 3L, 11L);

        /* limit 20으로 조회하면 조건을 만족하는 같은 회사 회의 두 건만 반환돼야 한다. */
        List<MeetingQueryRepository.UpcomingMeetingSnapshot> all =
                meetingQueryRepository.findUpcomingMeetings(10L, 7L, now, 20);

        /* 시작 시각이 가까운 순서이며 지난·타 회사·비참석 회의는 제외돼야 한다. */
        assertThat(all)
                .extracting(MeetingQueryRepository.UpcomingMeetingSnapshot::meetingId)
                .containsExactly(first.getId(), second.getId());

        /* 참석자 수는 회의별 추가 조회가 아닌 배치 집계 결과와 일치해야 한다. */
        assertThat(all)
                .extracting(MeetingQueryRepository.UpcomingMeetingSnapshot::attendeeCount)
                .containsExactly(4, 2);

        /* limit 1을 적용하면 전체 정렬 결과에서 가장 가까운 회의 한 건만 반환돼야 한다. */
        List<MeetingQueryRepository.UpcomingMeetingSnapshot> limited =
                meetingQueryRepository.findUpcomingMeetings(10L, 7L, now, 1);
        assertThat(limited)
                .extracting(MeetingQueryRepository.UpcomingMeetingSnapshot::meetingId)
                .containsExactly(first.getId());
    }

    /* 테스트 회의 조건으로 필수 컬럼을 모두 가진 영속성 엔티티를 만든다. */
    private MeetingJpaEntity meeting(
            Long companyId,
            Long projectId,
            String title,
            LocalDateTime startAt
    ) {
        /* 한 시간 예약과 개설자 한 명을 가진 정상 도메인 회의를 생성한다. */
        Meeting meeting = Meeting.create(
                companyId,
                projectId,
                100L,
                2L,
                3L,
                title,
                startAt,
                startAt.plusHours(1),
                false,
                null,
                List.of(3L)
        );

        /* 도메인 회의를 테스트 DB에 저장 가능한 완전한 엔티티로 변환한다. */
        return MeetingJpaEntity.from(meeting);
    }

    /* 회의와 여러 구성원 식별자로 참석자 행을 일괄 저장한다. */
    private void saveAttendees(Long meetingId, Long... memberIds) {
        /* 가변 인자 식별자를 각 복합 PK 엔티티로 변환해 한 번에 저장한다. */
        List<MeetingAttendeeJpaEntity> attendees = java.util.Arrays.stream(memberIds)
                .map(memberId -> new MeetingAttendeeJpaEntity(meetingId, memberId))
                .toList();
        springDataMeetingAttendeeRepository.saveAll(attendees);
    }
}
