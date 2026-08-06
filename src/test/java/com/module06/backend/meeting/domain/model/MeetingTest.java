package com.module06.backend.meeting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * MEET-01 회의 도메인의 참석자와 슬롯 생성 규칙을 검증하는 단위 테스트다.
 */
@DisplayName("MEET-01 회의 도메인")
class MeetingTest {

    /* 개설자가 자동 포함되고 중복 참석자가 한 번만 남는지 검증한다. */
    @Test
    @DisplayName("개설자를 첫 번째 참석자로 포함하고 중복 식별자를 제거한다")
    void includesHostFirstAndRemovesDuplicateAttendees() {
        /* 개설자도 요청 목록에 포함된 회의를 생성한다. */
        Meeting meeting = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L, 3L, 7L, 11L)
        );

        /* 개설자는 첫 번째에 한 번만 존재하고 나머지 참석자 순서가 유지돼야 한다. */
        assertThat(meeting.getAttendeeMemberIds()).containsExactly(3L, 7L, 11L);

        /* 신규 예약은 명세의 초기 상태를 가져야 한다. */
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);
        assertThat(meeting.getId()).isNull();
    }

    /* 시작 이상 종료 미만의 슬롯이 정확히 30분 단위로 생성되는지 검증한다. */
    @Test
    @DisplayName("예약 구간을 시작 포함 종료 제외 30분 슬롯으로 나눈다")
    void createsThirtyMinuteReservationSlots() {
        /* 한 시간 반 동안 진행되는 회의를 생성한다. */
        Meeting meeting = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 30),
                List.of(7L)
        );

        /* 종료 시각은 제외되고 세 개 슬롯 시작 시각만 반환돼야 한다. */
        assertThat(meeting.reservationSlotStarts()).containsExactly(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 14, 30),
                LocalDateTime.of(2026, 8, 6, 15, 0)
        );
    }

    /* 최초 입장과 재입장이 상태와 startedAt 불변식을 지키는지 검증한다. */
    @Test
    @DisplayName("최초 입장만 상태와 startedAt을 변경하고 재입장은 이를 유지한다")
    void entersMeetingIdempotently() {
        /* 아직 시작하지 않은 예약 회의와 최초 입장 시각을 준비한다. */
        Meeting scheduled = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L, 11L)
        );
        LocalDateTime firstEntryAt = LocalDateTime.of(2026, 8, 6, 13, 58);

        /* 최초 입장은 진행 상태와 startedAt을 가진 새 도메인 상태를 만든다. */
        Meeting inProgress = scheduled.enter(firstEntryAt);
        assertThat(inProgress.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(inProgress.getStartedAt()).isEqualTo(firstEntryAt);
        assertThat(inProgress.hasAttendee(7L)).isTrue();
        assertThat(inProgress.hasAttendee(99L)).isFalse();
        assertThat(inProgress.isHost(3L)).isTrue();

        /* 이후 재입장 시각을 전달해도 최초 상태 객체와 startedAt이 그대로 유지돼야 한다. */
        Meeting reentered = inProgress.enter(LocalDateTime.of(2026, 8, 6, 14, 10));
        assertThat(reentered).isSameAs(inProgress);
        assertThat(reentered.getStartedAt()).isEqualTo(firstEntryAt);
    }

    /* 진행 중 회의 종료가 DONE 상태와 실측 시간을 만드는지 검증한다. */
    @Test
    @DisplayName("진행 중 회의를 DONE으로 전이하고 실제 진행 분을 계산한다")
    void completesInProgressMeetingAndCalculatesDuration() {
        /* 최초 입장 시각이 13시 58분 12초인 진행 중 회의를 준비한다. */
        Meeting inProgress = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L, 11L)
        ).enter(LocalDateTime.of(2026, 8, 6, 13, 58, 12));

        /* 예약 종료와 무관한 실제 15시 2분 40초에 회의를 종료한다. */
        Meeting completed = inProgress.complete(LocalDateTime.of(2026, 8, 6, 15, 2, 40));

        /* 상태와 실제 종료 시각이 바뀌고 잔여 초를 버린 64분이 반환돼야 한다. */
        assertThat(completed.getStatus()).isEqualTo(MeetingStatus.DONE);
        assertThat(completed.getStartedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 13, 58, 12));
        assertThat(completed.getEndedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 15, 2, 40));
        assertThat(completed.actualDurationMinutes()).isEqualTo(64L);

        /* 종료 전 확정된 참석자 명단은 상태 전이 뒤에도 동일해야 한다. */
        assertThat(completed.getAttendeeMemberIds()).containsExactly(3L, 7L, 11L);
    }

    /* 시작되지 않았거나 이미 종료된 회의의 잘못된 완료 전이를 검증한다. */
    @Test
    @DisplayName("SCHEDULED와 DONE 회의의 종료 전이를 거절한다")
    void rejectsInvalidCompletionTransitions() {
        /* 아직 입장하지 않은 예약 회의는 바로 DONE으로 건너뛸 수 없다. */
        Meeting scheduled = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L)
        );
        assertThatThrownBy(() -> scheduled.complete(LocalDateTime.of(2026, 8, 6, 15, 0)))
                .isInstanceOf(IllegalStateException.class);

        /* 한 번 완료된 회의는 분석 중복 트리거를 막기 위해 다시 완료할 수 없다. */
        Meeting completed = scheduled
                .enter(LocalDateTime.of(2026, 8, 6, 13, 58))
                .complete(LocalDateTime.of(2026, 8, 6, 15, 0));
        assertThatThrownBy(() -> completed.complete(LocalDateTime.of(2026, 8, 6, 15, 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    /* 테스트마다 동일한 필수값으로 신규 회의를 생성한다. */
    private Meeting createMeeting(
            LocalDateTime startAt,
            LocalDateTime endAt,
            List<Long> attendeeMemberIds
    ) {
        /* 시간과 참석자 이외의 값은 정상 예약 계약으로 고정한다. */
        return Meeting.create(
                10L,
                12L,
                100L,
                2L,
                3L,
                " A커머스 온보딩 킥오프 ",
                startAt,
                endAt,
                true,
                305L,
                attendeeMemberIds
        );
    }
}
