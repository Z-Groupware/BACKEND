package com.module06.backend.meeting.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingEntryRepository;
import com.module06.backend.meeting.domain.repository.MeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingReservationSlotRepository;

/*
 * MEET-01의 회의·슬롯·참석자 원자 저장과 슬롯 PK 동시성 관문을 실제 JPA로 검증한다.
 */
@SpringBootTest
@DisplayName("MEET-01 회의 예약 영속성 어댑터")
class MeetingPersistenceAdapterTest {

    /* 애플리케이션 계층이 사용하는 실제 회의 저장 포트다. */
    @Autowired
    private MeetingRepository meetingRepository;

    /* MEET-07에서 회의 행 잠금 조회와 상태 저장에 사용하는 도메인 저장소 계약이다. */
    @Autowired
    private MeetingEntryRepository meetingEntryRepository;

    /* 저장된 회의 기본 행을 조회하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    /* 저장된 참석자 행을 조회하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* 저장된 회의실 슬롯 행을 조회하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingReservationSlotRepository springDataMeetingReservationSlotRepository;

    /* 서로 분리된 실제 트랜잭션에서 예약 충돌을 재현하기 위한 트랜잭션 관리자다. */
    @Autowired
    private PlatformTransactionManager transactionManager;

    /* 각 테스트가 같은 예약 슬롯 데이터를 공유하지 않도록 자식 행부터 초기화한다. */
    @BeforeEach
    void clearMeetingReservationData() {
        /* 슬롯과 참석자를 먼저 지운 뒤 회의 기본 행을 삭제한다. */
        springDataMeetingReservationSlotRepository.deleteAll();
        springDataMeetingAttendeeRepository.deleteAll();
        springDataMeetingRepository.deleteAll();
    }

    /* 신규 회의 저장 시 기본 행과 모든 슬롯·참석자가 함께 저장되는지 검증한다. */
    @Test
    @DisplayName("회의 한 건과 30분 슬롯, 개설자 포함 참석자를 원자적으로 저장한다")
    void savesMeetingSlotsAndAttendees() {
        /* 한 시간 예약과 세 명의 참석자가 있는 신규 회의를 준비한다. */
        Meeting meeting = meeting("A커머스 온보딩 킥오프", List.of(3L, 7L, 11L));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        /* 실제 트랜잭션 안에서 영속성 어댑터를 호출해 예약을 커밋한다. */
        Meeting savedMeeting = transaction.execute(status -> meetingRepository.saveReservation(meeting));

        /* 데이터베이스가 생성한 회의 식별자가 도메인 결과에 반영돼야 한다. */
        assertThat(savedMeeting).isNotNull();
        assertThat(savedMeeting.getId()).isNotNull();

        /* 한 시간 범위는 시작 포함 종료 제외 두 개의 30분 슬롯으로 저장돼야 한다. */
        assertThat(springDataMeetingReservationSlotRepository
                .findAllByMeetingIdOrderBySlotStartAsc(savedMeeting.getId()))
                .extracting(slot -> slot.getSlotStart())
                .containsExactly(
                        LocalDateTime.of(2026, 8, 6, 14, 0),
                        LocalDateTime.of(2026, 8, 6, 14, 30)
                );

        /* 개설자를 포함한 중복 없는 참석자 세 명이 저장돼야 한다. */
        assertThat(springDataMeetingAttendeeRepository
                .findAllByMeetingIdOrderByMemberIdAsc(savedMeeting.getId()))
                .extracting(attendee -> attendee.getMemberId())
                .containsExactly(3L, 7L, 11L);
    }

    /* 같은 회의실과 슬롯을 두 번 예약하면 두 번째 트랜잭션이 MT-002로 실패하는지 검증한다. */
    @Test
    @DisplayName("동일 회의실의 겹치는 슬롯은 MT-002로 차단하고 두 번째 회의를 롤백한다")
    void rejectsOverlappingReservationWithSlotPrimaryKey() {
        /* 각 호출이 독립적으로 커밋 또는 롤백되도록 트랜잭션 템플릿을 준비한다. */
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        /* 첫 번째 예약이 슬롯을 정상적으로 점유하게 한다. */
        transaction.executeWithoutResult(status -> meetingRepository.saveReservation(
                meeting("첫 번째 예약", List.of(3L, 7L))
        ));

        /* 같은 회의실과 동일 시간의 두 번째 예약은 DB 복합 PK 충돌로 MT-002가 돼야 한다. */
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> meetingRepository.saveReservation(
                meeting("두 번째 예약", List.of(3L, 11L))
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("MT-002");

        /* 실패한 두 번째 회의 기본 행도 같은 트랜잭션에서 롤백돼 첫 번째 회의만 남아야 한다. */
        assertThat(springDataMeetingRepository.count()).isEqualTo(1L);
        assertThat(springDataMeetingReservationSlotRepository.count()).isEqualTo(2L);
    }

    /* 기존 참석자와 목표 참석자의 추가·삭제 차이가 같은 트랜잭션에서 반영되는지 검증한다. */
    @Test
    @DisplayName("참석자 명단을 삭제·유지·추가 차이로 원자적으로 교체한다")
    void replacesMeetingAttendeesAtomically() {
        /* 기존 명단 3·7·11을 가진 회의를 먼저 커밋한다. */
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Meeting savedMeeting = transaction.execute(status -> meetingRepository.saveReservation(
                meeting("명단 교체 회의", List.of(3L, 7L, 11L))
        ));

        /* 7번을 제거하고 15번을 추가한 목표 명단으로 전체 교체한다. */
        transaction.executeWithoutResult(status -> meetingRepository.replaceAttendees(
                savedMeeting.getId(),
                List.of(3L, 11L, 15L)
        ));

        /* 유지된 구성원과 새 구성원만 남고 제거 대상은 즉시 사라져야 한다. */
        assertThat(springDataMeetingAttendeeRepository
                .findAllByMeetingIdOrderByMemberIdAsc(savedMeeting.getId()))
                .extracting(attendee -> attendee.getMemberId())
                .containsExactly(3L, 11L, 15L);
    }

    /* 입장용 잠금 조회가 회사 범위와 참석자 명단을 적용하고 상태를 저장하는지 검증한다. */
    @Test
    @DisplayName("회의를 잠금 조회하고 최초 입장 상태와 startedAt을 저장한다")
    void locksMeetingWithAttendeesAndSavesEntryState() {
        /* 예약 회의를 먼저 커밋해 회의·슬롯·참석자 행을 준비한다. */
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Meeting savedMeeting = transaction.execute(status -> meetingRepository.saveReservation(
                meeting("입장 테스트 회의", List.of(3L, 7L, 11L))
        ));
        LocalDateTime enteredAt = LocalDateTime.of(2026, 8, 6, 13, 58);

        /* 별도 트랜잭션에서 회사 범위 잠금 조회 후 도메인 입장 상태를 저장한다. */
        Meeting enteredMeeting = transaction.execute(status -> {
            Meeting locked = meetingEntryRepository
                    .findForEntry(10L, savedMeeting.getId())
                    .orElseThrow();

            /* 조회 결과는 최신 참석자 명단을 포함하고 타 회사 조건에서는 노출되지 않아야 한다. */
            assertThat(locked.getAttendeeMemberIds()).containsExactly(3L, 7L, 11L);
            assertThat(meetingEntryRepository.findForEntry(20L, savedMeeting.getId())).isEmpty();

            /* 최초 입장 도메인 상태를 기존 회의 행에 저장한다. */
            return meetingEntryRepository.saveState(locked.enter(enteredAt));
        });

        /* 저장 결과와 실제 meeting 행의 상태·startedAt이 동일해야 한다. */
        assertThat(enteredMeeting.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(enteredMeeting.getStartedAt()).isEqualTo(enteredAt);
        assertThat(springDataMeetingRepository.findById(savedMeeting.getId()))
                .get()
                .satisfies(entity -> {
                    assertThat(entity.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
                    assertThat(entity.getStartedAt()).isEqualTo(enteredAt);
                });

        /* 상태 저장은 예약 슬롯과 참석자 명단을 변경하면 안 된다. */
        assertThat(springDataMeetingReservationSlotRepository.count()).isEqualTo(2L);
        assertThat(springDataMeetingAttendeeRepository
                .findAllByMeetingIdOrderByMemberIdAsc(savedMeeting.getId()))
                .extracting(attendee -> attendee.getMemberId())
                .containsExactly(3L, 7L, 11L);
    }

    /* 테스트 제목과 참석자로 같은 회의실·시간의 신규 회의를 생성한다. */
    private Meeting meeting(String title, List<Long> attendees) {
        /* 영속성 검증에 필요한 모든 필드를 정상값으로 채운다. */
        return Meeting.create(
                10L,
                12L,
                100L,
                2L,
                3L,
                title,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                true,
                305L,
                attendees
        );
    }
}
