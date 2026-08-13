package com.module06.backend.meeting.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingListScope;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingTopicType;
import com.module06.backend.meeting.domain.repository.MeetingDetailRepository;
import com.module06.backend.meeting.domain.repository.MeetingListRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingTopicJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingReservationSlotRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingTopicRepository;

/*
 * E·C 연동 기반의 회사 격리·정렬·배치 조회를 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("회의 조회 영속성 어댑터")
class MeetingQueryPersistenceAdapterTest {

    /* 애플리케이션 계층이 사용하는 실제 회의 조회 저장소 계약이다. */
    @Autowired
    private MeetingQueryRepository meetingQueryRepository;

    /* MEET-02 동적 필터·권한·페이징 조회에 사용하는 목록 전용 저장소 계약이다. */
    @Autowired
    private MeetingListRepository meetingListRepository;

    /* MEET-04 회사 범위 상세 조회에 사용하는 전용 저장소 계약이다. */
    @Autowired
    private MeetingDetailRepository meetingDetailRepository;

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

    /* MEET-09 잠금 조회가 실제 영속성 컨텍스트에 적용한 잠금 모드를 확인한다. */
    @Autowired
    private EntityManager entityManager;

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

    /* MEET-04 상세 조회가 D 소유 필드와 참석자를 회사 범위에서 제공하는지 검증한다. */
    @Test
    @DisplayName("회의 상세 원본과 참석자를 회사 범위에서 조회한다")
    void findsMeetingDetailInsideCompanyScope() {
        /* 회사 10의 회의와 세 참석자를 저장하고 생성 시각을 데이터베이스에 반영한다. */
        MeetingJpaEntity meeting = springDataMeetingRepository.saveAndFlush(
                meeting(10L, 12L, "상세 조회 회의", LocalDateTime.of(2026, 8, 6, 14, 0))
        );
        saveAttendees(meeting.getId(), 3L, 7L, 11L);

        /* 같은 회사의 MEET-04 상세 원본을 조회한다. */
        assertThat(meetingDetailRepository.findMeetingDetail(10L, meeting.getId()))
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    /* 회의 연결 식별자·팀·녹음 동의·생성 시각이 엔티티 값과 일치해야 한다. */
                    assertThat(snapshot.projectId()).isEqualTo(12L);
                    assertThat(snapshot.teamId()).isEqualTo(100L);
                    assertThat(snapshot.meetingRoomId()).isEqualTo(2L);
                    assertThat(snapshot.recordingConsent()).isFalse();
                    assertThat(snapshot.createdAt()).isNotNull();

                    /* 참석자 식별자는 파생 쿼리의 안정적인 구성원 식별자 순서를 유지해야 한다. */
                    assertThat(snapshot.attendeeMemberIds()).containsExactly(3L, 7L, 11L);
                });

        /* 동일한 회의 식별자를 다른 회사로 조회하면 존재 여부가 노출되지 않아야 한다. */
        assertThat(meetingDetailRepository.findMeetingDetail(20L, meeting.getId())).isEmpty();
    }

    /* MEET-09가 기존 명단을 읽기 전에 회의 행에 쓰기 잠금을 획득하는지 검증한다. */
    @Test
    @DisplayName("참석자 교체 조회는 회의 행을 비관적 쓰기 잠금으로 선점한다")
    void locksMeetingRowBeforeReplacingAttendees() {
        /* 잠금 대상이 될 회사 10의 회의를 저장하고 INSERT를 데이터베이스에 반영한다. */
        MeetingJpaEntity savedMeeting = springDataMeetingRepository.saveAndFlush(
                meeting(10L, 12L, "참석자 교체 회의", LocalDateTime.of(2026, 8, 6, 14, 0))
        );

        /* 저장 시 관리되던 엔티티를 비워 잠금 조회가 데이터베이스를 실제로 거치게 한다. */
        entityManager.clear();

        /* MEET-09 전용 파생 쿼리로 같은 회사의 회의 행을 잠가 조회한다. */
        MeetingJpaEntity lockedMeeting = springDataMeetingRepository
                .findLockedByIdAndCompanyId(savedMeeting.getId(), 10L)
                .orElseThrow();

        /* 현재 트랜잭션이 대상 회의 엔티티에 PESSIMISTIC_WRITE 잠금을 보유해야 한다. */
        assertThat(entityManager.getLockMode(lockedMeeting)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
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

    /* 프로젝트별 회의 수 집계가 회사 범위와 취소 제외 정책을 지키는지 검증한다. */
    @Test
    @DisplayName("프로젝트별 SCHEDULED·IN_PROGRESS·DONE만 세고 CANCELED와 타 회사를 제외한다")
    void countsNonCanceledMeetingsByProjectInsideCompanyScope() {
        /* 회사 10의 12번 프로젝트에 예약 회의와 취소 회의를 각각 저장한다. */
        springDataMeetingRepository.save(meetingWithStatus(
                10L,
                12L,
                "예정 회의",
                MeetingStatus.SCHEDULED,
                LocalDateTime.of(2026, 8, 6, 14, 0)
        ));
        springDataMeetingRepository.save(meetingWithStatus(
                10L,
                12L,
                "취소 회의",
                MeetingStatus.CANCELED,
                LocalDateTime.of(2026, 8, 7, 14, 0)
        ));

        /* 회사 10의 13번 프로젝트에는 진행·완료 회의를 저장한다. */
        springDataMeetingRepository.save(meetingWithStatus(
                10L,
                13L,
                "진행 회의",
                MeetingStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 8, 8, 14, 0)
        ));
        springDataMeetingRepository.save(meetingWithStatus(
                10L,
                13L,
                "완료 회의",
                MeetingStatus.DONE,
                LocalDateTime.of(2026, 8, 9, 14, 0)
        ));

        /* 같은 12번 프로젝트라도 다른 회사의 회의는 집계에서 제외돼야 한다. */
        springDataMeetingRepository.save(meetingWithStatus(
                20L,
                12L,
                "다른 회사 회의",
                MeetingStatus.SCHEDULED,
                LocalDateTime.of(2026, 8, 10, 14, 0)
        ));

        /* 회사 10 범위에서 12·13·14번 프로젝트 회의 수를 한 번에 조회한다. */
        Map<Long, Long> counts = meetingQueryRepository.countMeetingsByProjectIds(
                10L,
                List.of(12L, 13L, 14L)
        );

        /* 취소·타 회사 회의는 빠지고 실제 회의가 있는 프로젝트의 집계만 반환돼야 한다. */
        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(
                12L, 1L,
                13L, 2L
        ));
        assertThat(counts).doesNotContainKey(14L);
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
        /* 안건 식별자가 계층 복원용으로 손실 없이 전달돼야 한다. */
        assertThat(result)
                .allSatisfy(snapshot -> assertThat(snapshot.topicId()).isNotNull());
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

    /* MEET-02 동적 필터와 일반 구성원 열람 범위 및 페이지 집계를 실제 JPA로 검증한다. */
    @Test
    @DisplayName("회의 목록을 회사·기간·상태·참석 권한으로 필터링하고 페이징한다")
    void findsFilteredMeetingPageInsideReadScope() {
        /* 같은 회사에서 요청자 7번이 참석한 최신 회의와 열람할 수 없는 이전 회의를 저장한다. */
        MeetingJpaEntity visible = springDataMeetingRepository.save(
                meeting(10L, 12L, "참석 회의", LocalDateTime.of(2026, 8, 6, 14, 0))
        );
        MeetingJpaEntity hidden = springDataMeetingRepository.save(
                meeting(10L, 12L, "비참석 회의", LocalDateTime.of(2026, 8, 5, 14, 0))
        );

        /* 같은 조건이지만 다른 회사에 속한 회의를 함께 저장해 테넌트 조건을 검증한다. */
        MeetingJpaEntity otherCompany = springDataMeetingRepository.save(
                meeting(20L, 12L, "다른 회사 회의", LocalDateTime.of(2026, 8, 7, 14, 0))
        );

        /* 요청자는 첫 회의와 타 회사 회의에 참석하고 숨김 회의에는 참석하지 않는다. */
        saveAttendees(visible.getId(), 3L, 7L, 11L);
        saveAttendees(hidden.getId(), 3L, 11L);
        saveAttendees(otherCompany.getId(), 30L, 7L);

        /* 일반 구성원 7번의 회사·프로젝트·회의실·기간·상태 필터 페이지를 조회한다. */
        MeetingListRepository.MeetingPage restrictedPage =
                meetingListRepository.findMeetings(
                        new MeetingListRepository.MeetingListCriteria(
                                10L,
                                7L,
                                false,
                                12L,
                                2L,
                                LocalDateTime.of(2026, 8, 1, 0, 0),
                                LocalDateTime.of(2026, 8, 31, 23, 59, 59),
                                MeetingStatus.SCHEDULED,
                                null,
                                0,
                                20
                        )
                );

        /* 타 회사와 비참석 회의는 제외되고 참석 회의 한 건만 반환돼야 한다. */
        assertThat(restrictedPage.meetings())
                .extracting(MeetingListRepository.MeetingListSnapshot::meetingId)
                .containsExactly(visible.getId());
        assertThat(restrictedPage.totalElements()).isEqualTo(1L);

        /* 참석자 식별자는 페이지 회의의 배치 조회 결과와 동일해야 한다. */
        assertThat(restrictedPage.meetings().get(0).attendeeMemberIds()).containsExactly(3L, 7L, 11L);

        /* 회사 전체 권한으로 1건 페이지를 조회하면 최신 회의와 전체 2건 메타가 반환돼야 한다. */
        MeetingListRepository.MeetingPage companyWidePage =
                meetingListRepository.findMeetings(
                        new MeetingListRepository.MeetingListCriteria(
                                10L,
                                3L,
                                true,
                                12L,
                                2L,
                                LocalDateTime.of(2026, 8, 1, 0, 0),
                                LocalDateTime.of(2026, 8, 31, 23, 59, 59),
                                MeetingStatus.SCHEDULED,
                                null,
                                0,
                                1
                        )
                );

        /* startAt과 id 내림차순 및 전체 페이지 수 계산이 안정적으로 적용돼야 한다. */
        assertThat(companyWidePage.meetings())
                .extracting(MeetingListRepository.MeetingListSnapshot::meetingId)
                .containsExactly(visible.getId());
        assertThat(companyWidePage.totalElements()).isEqualTo(2L);
        assertThat(companyWidePage.totalPages()).isEqualTo(2);
    }

    /* scope=HOSTED·ATTENDING이 companyWideRead와 무관하게 요청자 본인 기준으로 좁히는지 검증한다. */
    @Test
    @DisplayName("scope=HOSTED·ATTENDING은 회사 전체 열람 권한과 무관하게 본인 기준으로 좁힌다")
    void findsMeetingsByScopeRegardlessOfCompanyWideRead() {
        /* 요청자 3번이 host인 회의와 참석자로만 등록된 회의, 그리고 타 회사에서 host인 회의를 저장한다. */
        MeetingJpaEntity hosted = springDataMeetingRepository.save(
                meeting(10L, 12L, "내가 개설한 회의", LocalDateTime.of(2026, 8, 6, 14, 0), 3L)
        );
        saveAttendees(hosted.getId(), 3L, 7L);

        MeetingJpaEntity attending = springDataMeetingRepository.save(
                meeting(10L, 12L, "내가 참석하는 회의", LocalDateTime.of(2026, 8, 5, 14, 0), 99L)
        );
        saveAttendees(attending.getId(), 99L, 3L);

        MeetingJpaEntity otherCompanyHosted = springDataMeetingRepository.save(
                meeting(20L, 12L, "다른 회사에서 내가 개설한 회의", LocalDateTime.of(2026, 8, 4, 14, 0), 3L)
        );
        saveAttendees(otherCompanyHosted.getId(), 3L);

        /* OWNER의 companyWideRead=true를 함께 넘겨도 scope=HOSTED면 본인 host 회의만 나와야 한다. */
        MeetingListRepository.MeetingPage hostedPage = meetingListRepository.findMeetings(
                new MeetingListRepository.MeetingListCriteria(
                        10L, 3L, true, null, null,
                        LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59, 59),
                        null, MeetingListScope.HOSTED, 0, 20
                )
        );
        assertThat(hostedPage.meetings())
                .extracting(MeetingListRepository.MeetingListSnapshot::meetingId)
                .containsExactly(hosted.getId());

        /* ATTENDING은 참석자이면서 host는 아닌 회의만 남기고 본인이 개설한 회의는 제외해야 한다. */
        MeetingListRepository.MeetingPage attendingPage = meetingListRepository.findMeetings(
                new MeetingListRepository.MeetingListCriteria(
                        10L, 3L, true, null, null,
                        LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59, 59),
                        null, MeetingListScope.ATTENDING, 0, 20
                )
        );
        assertThat(attendingPage.meetings())
                .extracting(MeetingListRepository.MeetingListSnapshot::meetingId)
                .containsExactly(attending.getId());
    }

    /* 3번을 개설자로 하는 기본 테스트 회의 엔티티를 만든다. */
    private MeetingJpaEntity meeting(
            Long companyId,
            Long projectId,
            String title,
            LocalDateTime startAt
    ) {
        /* 개설자를 지정하지 않는 기존 호출부와의 호환을 위해 기본 개설자 3번을 사용한다. */
        return meeting(companyId, projectId, title, startAt, 3L);
    }

    /* 테스트 회의 조건과 지정 개설자로 필수 컬럼을 모두 가진 영속성 엔티티를 만든다. */
    private MeetingJpaEntity meeting(
            Long companyId,
            Long projectId,
            String title,
            LocalDateTime startAt,
            Long hostMemberId
    ) {
        /* 한 시간 예약과 지정 개설자를 가진 정상 도메인 회의를 생성한다. */
        Meeting meeting = Meeting.create(
                companyId,
                projectId,
                100L,
                2L,
                hostMemberId,
                title,
                startAt,
                startAt.plusHours(1),
                false,
                null,
                List.of(hostMemberId)
        );

        /* 도메인 회의를 테스트 DB에 저장 가능한 완전한 엔티티로 변환한다. */
        return MeetingJpaEntity.from(meeting);
    }

    /* 지정 상태와 프로젝트를 가진 프로젝트 회의 수 집계용 영속성 엔티티를 만든다. */
    private MeetingJpaEntity meetingWithStatus(
            Long companyId,
            Long projectId,
            String title,
            MeetingStatus status,
            LocalDateTime startAt
    ) {
        /* 모든 상태 전이의 출발점이 되는 정상 예약 회의를 생성한다. */
        Meeting scheduled = Meeting.create(
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

        /* 집계 정책의 네 상태를 실제 도메인 전이로 만들어 잘못된 시각 조합을 피한다. */
        Meeting target = switch (status) {
            case SCHEDULED -> scheduled;
            case IN_PROGRESS -> scheduled.enter(startAt.minusMinutes(2));
            case DONE -> scheduled.enter(startAt.minusMinutes(2)).complete(startAt.plusHours(1));
            case CANCELED -> scheduled.cancel(startAt.minusHours(1));
        };

        /* 상태 전이가 끝난 도메인 회의를 저장 가능한 엔티티로 변환한다. */
        return MeetingJpaEntity.from(target);
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
