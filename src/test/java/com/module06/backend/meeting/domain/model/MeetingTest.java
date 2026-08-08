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

    /* 예약 회의 수정이 MEET-05 소유 값만 바꾸고 참석자·개설자·상태를 유지하는지 검증한다. */
    @Test
    @DisplayName("예약 회의 수정은 메타와 슬롯 범위만 바꾸고 참석자와 상태를 유지한다")
    void updatesScheduledMeetingWithoutChangingOwnership() {
        /* 개설자와 두 참석자를 가진 기존 예약 회의를 준비한다. */
        Meeting scheduled = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L, 11L)
        );

        /* 프로젝트·회의실·제목·예약 시간·녹음 동의를 새로운 최종 값으로 변경한다. */
        Meeting updated = scheduled.updateSchedule(
                13L,
                4L,
                " 변경된 정기 회의 ",
                LocalDateTime.of(2026, 8, 6, 15, 0),
                LocalDateTime.of(2026, 8, 6, 16, 0),
                false
        );

        /* MEET-05 대상 필드는 정규화된 최종값으로 교체돼야 한다. */
        assertThat(updated.getProjectId()).isEqualTo(13L);
        assertThat(updated.getMeetingRoomId()).isEqualTo(4L);
        assertThat(updated.getTitle()).isEqualTo("변경된 정기 회의");
        assertThat(updated.getStartAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 15, 0));
        assertThat(updated.getEndAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 16, 0));
        assertThat(updated.isRecordingConsent()).isFalse();

        /* 개설자·참석자·상태·관련 액션은 수정 API 범위 밖이므로 그대로 유지돼야 한다. */
        assertThat(updated.getHostMemberId()).isEqualTo(3L);
        assertThat(updated.getAttendeeMemberIds()).containsExactly(3L, 7L, 11L);
        assertThat(updated.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);
        assertThat(updated.getRelatedActionId()).isEqualTo(305L);
    }

    /* 진행 중 회의를 도메인 메서드로 직접 수정하는 잘못된 내부 호출을 거절하는지 검증한다. */
    @Test
    @DisplayName("시작된 회의는 예약 정보 수정으로 되돌릴 수 없다")
    void rejectsUpdatingStartedMeeting() {
        /* 예약 회의를 최초 입장 처리해 IN_PROGRESS 상태로 만든다. */
        Meeting inProgress = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L)
        ).enter(LocalDateTime.of(2026, 8, 6, 13, 58));

        /* 서비스 검증을 우회하더라도 진행 회의의 예약 정보 변경은 도메인에서 차단돼야 한다. */
        assertThatThrownBy(() -> inProgress.updateSchedule(
                13L,
                4L,
                "변경 시도",
                LocalDateTime.of(2026, 8, 6, 15, 0),
                LocalDateTime.of(2026, 8, 6, 16, 0),
                false
        )).isInstanceOf(IllegalStateException.class);
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

    /* 시작 전 취소가 상태·시각을 바꾸고 예약·참석자 이력을 보존하는지 검증한다. */
    @Test
    @DisplayName("SCHEDULED 회의를 CANCELED로 전이하고 최초 취소 시각을 보존한다")
    void cancelsScheduledMeetingIdempotently() {
        /* 정상 예약 회의와 서로 다른 최초·재요청 취소 시각을 준비한다. */
        Meeting scheduled = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L, 11L)
        );
        LocalDateTime firstCanceledAt = LocalDateTime.of(2026, 8, 6, 10, 0);

        /* 최초 취소는 상태와 취소·수정 시각만 변경해야 한다. */
        Meeting canceled = scheduled.cancel(firstCanceledAt);
        assertThat(canceled.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(canceled.getCanceledAt()).isEqualTo(firstCanceledAt);
        assertThat(canceled.getUpdatedAt()).isEqualTo(firstCanceledAt);
        assertThat(canceled.getAttendeeMemberIds()).containsExactly(3L, 7L, 11L);
        assertThat(canceled.getStartAt()).isEqualTo(scheduled.getStartAt());

        /* 재취소는 같은 객체를 반환해 최초 취소 시각을 덮어쓰지 않아야 한다. */
        Meeting recanceled = canceled.cancel(LocalDateTime.of(2026, 8, 6, 10, 5));
        assertThat(recanceled).isSameAs(canceled);
        assertThat(recanceled.getCanceledAt()).isEqualTo(firstCanceledAt);
    }

    /* 진행·완료 회의와 취소 시각 누락이 도메인 전이에서 차단되는지 검증한다. */
    @Test
    @DisplayName("시작된 회의와 취소 시각이 없는 요청은 취소할 수 없다")
    void rejectsInvalidCancellationTransitions() {
        /* 진행 중 회의는 확정된 실제 시작 시각 때문에 CANCELED로 되돌릴 수 없다. */
        Meeting inProgress = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L)
        ).enter(LocalDateTime.of(2026, 8, 6, 13, 58));
        assertThatThrownBy(() -> inProgress.cancel(LocalDateTime.of(2026, 8, 6, 14, 5)))
                .isInstanceOf(IllegalStateException.class);

        /* SCHEDULED 회의도 취소 이력 시각이 없으면 유효한 취소 상태를 만들 수 없다. */
        Meeting scheduled = createMeeting(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L)
        );
        assertThatThrownBy(() -> scheduled.cancel(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* 취소 시각 없는 오버로드로 CANCELED 회의를 복원하려는 시도가 차단되는지 검증한다. */
    @Test
    @DisplayName("취소 시각을 받지 않는 복원 오버로드로는 CANCELED 회의를 만들 수 없다")
    void rejectsCanceledStatusOnLegacyReconstitute() {
        /* 이 오버로드는 canceledAt을 null로 고정하므로 CANCELED 복원 자체를 거절해야 한다. */
        assertThatThrownBy(() -> reconstituteWithoutCanceledAt(MeetingStatus.CANCELED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canceledAt");

        /* 취소되지 않은 상태는 기존 호출부 그대로 정상 복원돼야 한다. */
        assertThat(reconstituteWithoutCanceledAt(MeetingStatus.SCHEDULED).getCanceledAt()).isNull();
    }

    /* 상태와 취소 시각이 어긋난 회의가 복원되지 않는지 검증한다. */
    @Test
    @DisplayName("상태와 취소 시각이 어긋난 회의는 복원할 수 없다")
    void rejectsInconsistentCancellationState() {
        /* CANCELED인데 취소 시각이 없으면 취소 이력의 시간 계약이 빈다. */
        assertThatThrownBy(() -> reconstituteWithCanceledAt(MeetingStatus.CANCELED, null))
                .isInstanceOf(IllegalArgumentException.class);

        /* 취소되지 않은 회의에 취소 시각만 남아 있으면 상태와 취소 여부가 갈린다. */
        assertThatThrownBy(() -> reconstituteWithCanceledAt(
                MeetingStatus.SCHEDULED,
                LocalDateTime.of(2026, 8, 6, 10, 0)
        ))
                .isInstanceOf(IllegalArgumentException.class);

        /* 상태와 취소 시각이 맞으면 그대로 복원돼야 한다. */
        LocalDateTime canceledAt = LocalDateTime.of(2026, 8, 6, 10, 0);
        Meeting canceled = reconstituteWithCanceledAt(MeetingStatus.CANCELED, canceledAt);
        assertThat(canceled.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(canceled.getCanceledAt()).isEqualTo(canceledAt);
    }

    /* 취소 시각을 받지 않는 기존 오버로드로 회의를 복원한다. */
    private Meeting reconstituteWithoutCanceledAt(MeetingStatus status) {
        /* 취소 시각 컬럼 도입 전 호출부와 동일한 인자 구성을 사용한다. */
        return Meeting.reconstitute(
                91L,
                10L,
                12L,
                100L,
                2L,
                3L,
                "주간 회의",
                status,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                true,
                305L,
                List.of(3L, 7L),
                null,
                null,
                LocalDateTime.of(2026, 8, 5, 10, 0),
                LocalDateTime.of(2026, 8, 5, 10, 0)
        );
    }

    /* 취소 시각을 받는 오버로드로 회의를 복원한다. */
    private Meeting reconstituteWithCanceledAt(MeetingStatus status, LocalDateTime canceledAt) {
        /* 영속성 어댑터와 동일하게 저장된 모든 값을 그대로 전달한다. */
        return Meeting.reconstitute(
                91L,
                10L,
                12L,
                100L,
                2L,
                3L,
                "주간 회의",
                status,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                true,
                305L,
                List.of(3L, 7L),
                null,
                null,
                canceledAt,
                LocalDateTime.of(2026, 8, 5, 10, 0),
                LocalDateTime.of(2026, 8, 5, 10, 0)
        );
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
