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
import com.module06.backend.meeting.domain.repository.MeetingCancellationRepository;
import com.module06.backend.meeting.domain.repository.MeetingCompletionRepository;
import com.module06.backend.meeting.domain.repository.MeetingEntryRepository;
import com.module06.backend.meeting.domain.repository.MeetingRepository;
import com.module06.backend.meeting.domain.repository.MeetingUpdateRepository;
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

    /* MEET-08에서 회의 행 잠금 조회와 완료 상태 저장에 사용하는 도메인 저장소 계약이다. */
    @Autowired
    private MeetingCompletionRepository meetingCompletionRepository;

    /* MEET-05에서 회사 범위 잠금 조회와 예약 슬롯 교체 저장에 사용하는 도메인 저장소다. */
    @Autowired
    private MeetingUpdateRepository meetingUpdateRepository;

    /* MEET-06에서 회사 범위 잠금 조회와 취소 상태·슬롯 해제 저장에 사용하는 도메인 저장소다. */
    @Autowired
    private MeetingCancellationRepository meetingCancellationRepository;

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

    /* 완료 저장이 실제 시각만 갱신하고 예약 슬롯과 확정 참석자를 유지하는지 검증한다. */
    @Test
    @DisplayName("회의를 잠금 조회하고 DONE 상태를 저장하되 슬롯과 참석자를 유지한다")
    void locksAndCompletesMeetingWithoutDeletingHistory() {
        /* 예약 회의를 저장한 뒤 최초 입장으로 IN_PROGRESS 상태를 먼저 만든다. */
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Meeting savedMeeting = transaction.execute(status -> meetingRepository.saveReservation(
                meeting("종료 테스트 회의", List.of(3L, 7L, 11L))
        ));
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 6, 13, 58, 12);
        transaction.executeWithoutResult(status -> {
            /* 실제 MEET-07 저장 경로로 최초 시작 상태를 반영한다. */
            Meeting locked = meetingEntryRepository.findForEntry(10L, savedMeeting.getId()).orElseThrow();
            meetingEntryRepository.saveState(locked.enter(startedAt));
        });

        /* 별도 종료 트랜잭션에서 회사 범위 잠금 조회 후 DONE과 endedAt을 저장한다. */
        LocalDateTime endedAt = LocalDateTime.of(2026, 8, 6, 15, 2, 40);
        Meeting completedMeeting = transaction.execute(status -> {
            Meeting locked = meetingCompletionRepository
                    .findForCompletion(10L, savedMeeting.getId())
                    .orElseThrow();

            /* 타 회사 조건에서는 같은 식별자의 회의를 노출하면 안 된다. */
            assertThat(meetingCompletionRepository.findForCompletion(20L, savedMeeting.getId())).isEmpty();

            /* 도메인 완료 상태를 기존 meeting 행에 저장한다. */
            return meetingCompletionRepository.saveCompleted(locked.complete(endedAt));
        });

        /* 저장 결과와 실제 meeting 행은 DONE, 실제 종료 시각, 64분을 가져야 한다. */
        assertThat(completedMeeting.getStatus()).isEqualTo(MeetingStatus.DONE);
        assertThat(completedMeeting.getEndedAt()).isEqualTo(endedAt);
        assertThat(completedMeeting.actualDurationMinutes()).isEqualTo(64L);
        assertThat(springDataMeetingRepository.findById(savedMeeting.getId()))
                .get()
                .satisfies(entity -> {
                    assertThat(entity.getStatus()).isEqualTo(MeetingStatus.DONE);
                    assertThat(entity.getStartedAt()).isEqualTo(startedAt);
                    assertThat(entity.getEndedAt()).isEqualTo(endedAt);
                });

        /* 종료 뒤에도 예약 이력 슬롯 두 개와 확정 참석자 세 명이 그대로 남아야 한다. */
        assertThat(springDataMeetingReservationSlotRepository
                .findAllByMeetingIdOrderBySlotStartAsc(savedMeeting.getId()))
                .hasSize(2);
        assertThat(springDataMeetingAttendeeRepository
                .findAllByMeetingIdOrderByMemberIdAsc(savedMeeting.getId()))
                .extracting(attendee -> attendee.getMemberId())
                .containsExactly(3L, 7L, 11L);
    }

    /* 회의 수정이 meeting 행과 최종 예약 슬롯을 같은 트랜잭션에 반영하는지 검증한다. */
    @Test
    @DisplayName("예약 시간 수정은 meeting 값과 슬롯 차이를 원자적으로 저장한다")
    void updatesMeetingAndReservationSlotsAtomically() {
        /* 14시부터 15시까지 예약한 회의를 먼저 커밋한다. */
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Meeting savedMeeting = transaction.execute(status -> meetingRepository.saveReservation(
                meeting("수정 전 회의", List.of(3L, 7L, 11L))
        ));

        /* 별도 트랜잭션에서 회의를 잠그고 15시부터 16시 예약으로 이동한다. */
        Meeting updatedMeeting = transaction.execute(status -> {
            Meeting locked = meetingUpdateRepository.findForUpdate(10L, savedMeeting.getId()).orElseThrow();
            Meeting updated = locked.updateSchedule(
                    13L,
                    2L,
                    "수정 후 회의",
                    LocalDateTime.of(2026, 8, 6, 15, 0),
                    LocalDateTime.of(2026, 8, 6, 16, 0),
                    false
            );
            return meetingUpdateRepository.saveUpdate(updated, true);
        });

        /* meeting 기본 행은 프로젝트·제목·시간·녹음 동의의 최종값을 가져야 한다. */
        assertThat(updatedMeeting.getProjectId()).isEqualTo(13L);
        assertThat(updatedMeeting.getTitle()).isEqualTo("수정 후 회의");
        assertThat(updatedMeeting.getStartAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 15, 0));
        assertThat(updatedMeeting.isRecordingConsent()).isFalse();

        /* 기존 14시 슬롯은 사라지고 최종 15시·15시 30분 슬롯만 같은 회의에 남아야 한다. */
        assertThat(springDataMeetingReservationSlotRepository
                .findAllByMeetingIdOrderBySlotStartAsc(savedMeeting.getId()))
                .extracting(slot -> slot.getSlotStart())
                .containsExactly(
                        LocalDateTime.of(2026, 8, 6, 15, 0),
                        LocalDateTime.of(2026, 8, 6, 15, 30)
                );

        /* MEET-05는 참석자 명단을 수정하지 않으므로 기존 세 명이 그대로 유지돼야 한다. */
        assertThat(springDataMeetingAttendeeRepository
                .findAllByMeetingIdOrderByMemberIdAsc(savedMeeting.getId()))
                .extracting(attendee -> attendee.getMemberId())
                .containsExactly(3L, 7L, 11L);
    }

    /* 신규 슬롯 충돌 시 기존 meeting과 슬롯이 모두 롤백되는지 검증한다. */
    @Test
    @DisplayName("변경할 슬롯이 점유됐으면 MT-002를 반환하고 기존 예약을 유지한다")
    void rollsBackMeetingUpdateWhenNewSlotConflicts() {
        /* 수정 대상 14시 회의와 동일 회의실의 15시 차단 회의를 각각 커밋한다. */
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Meeting target = transaction.execute(status -> meetingRepository.saveReservation(
                meetingAt(
                        "수정 대상",
                        2L,
                        LocalDateTime.of(2026, 8, 6, 14, 0),
                        LocalDateTime.of(2026, 8, 6, 15, 0),
                        List.of(3L, 7L)
                )
        ));
        transaction.executeWithoutResult(status -> meetingRepository.saveReservation(
                meetingAt(
                        "기존 15시 예약",
                        2L,
                        LocalDateTime.of(2026, 8, 6, 15, 0),
                        LocalDateTime.of(2026, 8, 6, 16, 0),
                        List.of(3L, 11L)
                )
        ));

        /* 수정 대상을 이미 점유된 15시 슬롯으로 이동하면 MT-002로 실패해야 한다. */
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            Meeting locked = meetingUpdateRepository.findForUpdate(10L, target.getId()).orElseThrow();
            Meeting conflicting = locked.updateSchedule(
                    13L,
                    2L,
                    "롤백돼야 할 제목",
                    LocalDateTime.of(2026, 8, 6, 15, 0),
                    LocalDateTime.of(2026, 8, 6, 16, 0),
                    false
            );
            meetingUpdateRepository.saveUpdate(conflicting, true);
        }))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("MT-002");

        /* 실패한 트랜잭션의 meeting UPDATE도 롤백돼 원래 제목·프로젝트·시간이 유지돼야 한다. */
        assertThat(springDataMeetingRepository.findById(target.getId()))
                .get()
                .satisfies(entity -> {
                    assertThat(entity.getTitle()).isEqualTo("수정 대상");
                    assertThat(entity.getProjectId()).isEqualTo(12L);
                    assertThat(entity.getStartAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 14, 0));
                    assertThat(entity.getEndAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 15, 0));
                    assertThat(entity.isRecordingConsent()).isTrue();
                });

        /* 기존 14시 예약 슬롯도 삭제되지 않고 두 칸 모두 유지돼야 한다. */
        assertThat(springDataMeetingReservationSlotRepository
                .findAllByMeetingIdOrderBySlotStartAsc(target.getId()))
                .extracting(slot -> slot.getSlotStart())
                .containsExactly(
                        LocalDateTime.of(2026, 8, 6, 14, 0),
                        LocalDateTime.of(2026, 8, 6, 14, 30)
                );
    }

    /* 회의 취소가 기본 행과 참석자를 보존하면서 예약 슬롯만 해제하는지 검증한다. */
    @Test
    @DisplayName("회의를 CANCELED로 저장하고 예약 슬롯만 해제해 이력을 보존한다")
    void cancelsMeetingAndReleasesOnlyReservationSlots() {
        /* 슬롯 두 개와 참석자 세 명을 가진 예약 회의를 먼저 커밋한다. */
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Meeting savedMeeting = transaction.execute(status -> meetingRepository.saveReservation(
                meeting("취소 대상 회의", List.of(3L, 7L, 11L))
        ));
        LocalDateTime canceledAt = LocalDateTime.of(2026, 8, 6, 10, 30);

        /* 별도 트랜잭션에서 회사 범위 잠금 조회 후 취소 상태와 슬롯 해제를 저장한다. */
        Meeting canceledMeeting = transaction.execute(status -> {
            Meeting locked = meetingCancellationRepository
                    .findForCancellation(10L, savedMeeting.getId())
                    .orElseThrow();

            /* 다른 회사 조건으로는 같은 회의 식별자가 노출되지 않아야 한다. */
            assertThat(meetingCancellationRepository.findForCancellation(20L, savedMeeting.getId()))
                    .isEmpty();

            /* 도메인의 취소 상태를 기존 meeting 행에 저장하고 현재 슬롯을 해제한다. */
            return meetingCancellationRepository.saveCancellationAndReleaseSlots(
                    locked.cancel(canceledAt)
            );
        });

        /* 저장 결과와 실제 meeting 행은 CANCELED와 동일한 취소 시각을 가져야 한다. */
        assertThat(canceledMeeting.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(canceledMeeting.getCanceledAt()).isEqualTo(canceledAt);
        assertThat(springDataMeetingRepository.findById(savedMeeting.getId()))
                .get()
                .satisfies(entity -> {
                    assertThat(entity.getStatus()).isEqualTo(MeetingStatus.CANCELED);
                    assertThat(entity.getCanceledAt()).isEqualTo(canceledAt);
                });

        /* 취소한 회의의 슬롯은 사라지지만 회의 기본 행과 참석자 이력은 그대로 남아야 한다. */
        assertThat(springDataMeetingReservationSlotRepository
                .findAllByMeetingIdOrderBySlotStartAsc(savedMeeting.getId()))
                .isEmpty();
        assertThat(springDataMeetingRepository.existsById(savedMeeting.getId())).isTrue();
        assertThat(springDataMeetingAttendeeRepository
                .findAllByMeetingIdOrderByMemberIdAsc(savedMeeting.getId()))
                .extracting(attendee -> attendee.getMemberId())
                .containsExactly(3L, 7L, 11L);
    }

    /* 테스트 제목과 참석자로 같은 회의실·시간의 신규 회의를 생성한다. */
    private Meeting meeting(String title, List<Long> attendees) {
        /* 기존 테스트의 회의실과 한 시간 예약을 공통 생성기로 전달한다. */
        return meetingAt(
                title,
                2L,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                attendees
        );
    }

    /* 지정한 회의실과 시간으로 영속성 검증용 신규 회의를 생성한다. */
    private Meeting meetingAt(
            String title,
            Long meetingRoomId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            List<Long> attendees
    ) {
        /* 선택값 이외의 회사·프로젝트·host·팀 값은 정상 예약 계약으로 고정한다. */
        return Meeting.create(
                10L,
                12L,
                100L,
                meetingRoomId,
                3L,
                title,
                startAt,
                endAt,
                true,
                305L,
                attendees
        );
    }
}
