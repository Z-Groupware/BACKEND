package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.command.EnterMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingEntryResult;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingEntryRepository;

/*
 * MEET-07 서비스의 최초 입장·재입장·참석자 권한·입장 시간·종료 상태 규칙을 검증한다.
 */
@DisplayName("MEET-07 회의 입장 서비스")
class MeetingEntryServiceTest {

    /* 정상 입장 기준을 2026년 8월 6일 13시 58분 KST로 고정한다. */
    private static final Clock ENTRY_CLOCK = fixedClock("2026-08-06T04:58:00Z");

    /* 첫 입장이 상태와 startedAt을 변경하고 개설자 화면 정보를 반환하는지 검증한다. */
    @Test
    @DisplayName("첫 참석자 입장에서 SCHEDULED를 IN_PROGRESS로 전이한다")
    void startsScheduledMeetingOnFirstEntry() {
        /* 개설자 3번이 포함된 예약 회의와 저장소 대역을 준비한다. */
        RecordingMeetingEntryRepository repository = new RecordingMeetingEntryRepository(
                meeting(MeetingStatus.SCHEDULED, null)
        );
        MeetingEntryService service = new MeetingEntryService(repository, ENTRY_CLOCK);

        /* 개설자 3번의 정상 입장 요청을 실행한다. */
        MeetingEntryResult result = service.enterMeeting(command(3L));

        /* 최초 입장 시 상태와 시작 시각이 바뀌고 전체 참석자 수와 녹음 안내값이 반환돼야 한다. */
        assertThat(result.meetingId()).isEqualTo(91L);
        assertThat(result.status()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(result.startedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 13, 58));
        assertThat(result.attendeeCount()).isEqualTo(3);
        assertThat(result.recordingConsent()).isTrue();
        assertThat(result.isHost()).isTrue();
        assertThat(result.canControlRecording()).isTrue();

        /* 저장된 회의도 동일한 최초 시작 상태를 가져야 한다. */
        assertThat(repository.saveCalls).isEqualTo(1);
        assertThat(repository.savedMeeting.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(repository.savedMeeting.getStartedAt()).isEqualTo(result.startedAt());
    }

    /* 진행 중 회의 재입장이 기존 startedAt을 보존하고 추가 저장을 하지 않는지 검증한다. */
    @Test
    @DisplayName("IN_PROGRESS 회의 재입장은 startedAt을 유지하는 멱등 요청이다")
    void reentersInProgressMeetingIdempotently() {
        /* 최초 시작 시각이 이미 기록된 진행 중 회의를 준비한다. */
        LocalDateTime originalStartedAt = LocalDateTime.of(2026, 8, 6, 13, 55);
        RecordingMeetingEntryRepository repository = new RecordingMeetingEntryRepository(
                meeting(MeetingStatus.IN_PROGRESS, originalStartedAt)
        );
        MeetingEntryService service = new MeetingEntryService(repository, ENTRY_CLOCK);

        /* 일반 참석자 7번이 같은 회의에 재입장한다. */
        MeetingEntryResult result = service.enterMeeting(command(7L));

        /* 현재 시각으로 덮어쓰지 않고 최초 startedAt과 진행 상태를 그대로 반환해야 한다. */
        assertThat(result.status()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(result.startedAt()).isEqualTo(originalStartedAt);
        assertThat(result.isHost()).isFalse();
        assertThat(result.canControlRecording()).isTrue();
        assertThat(repository.saveCalls).isZero();
    }

    /* 회사 역할이 높아도 예약 명단에 없으면 입장할 수 없는지 검증한다. */
    @Test
    @DisplayName("예약 참석자가 아니면 MT-007로 거절한다")
    void rejectsMemberOutsideRoster() {
        /* 정상 시간의 예약 회의를 반환하는 서비스를 준비한다. */
        RecordingMeetingEntryRepository repository = new RecordingMeetingEntryRepository(
                meeting(MeetingStatus.SCHEDULED, null)
        );
        MeetingEntryService service = new MeetingEntryService(repository, ENTRY_CLOCK);

        /* 명단에 없는 99번 사용자는 저장 전에 참석자 전용 오류를 받아야 한다. */
        assertErrorCode(() -> service.enterMeeting(command(99L)), "MT-007");
        assertThat(repository.saveCalls).isZero();
    }

    /* 입장 허용 시작보다 이른 요청을 구분된 오류로 처리하는지 검증한다. */
    @Test
    @DisplayName("입장 허용 시각 전에는 MT-008로 거절한다")
    void rejectsEntryBeforeWindow() {
        /* 시작 10분 전보다 1초 이른 KST 13:49:59 시계를 준비한다. */
        Clock earlyClock = fixedClock("2026-08-06T04:49:59Z");
        MeetingEntryService service = new MeetingEntryService(
                new RecordingMeetingEntryRepository(meeting(MeetingStatus.SCHEDULED, null)),
                earlyClock
        );

        /* 예약 참석자라도 입장 창이 열리기 전에는 MT-008이어야 한다. */
        assertErrorCode(() -> service.enterMeeting(command(3L)), "MT-008");
    }

    /* 종료 시각이 지난 요청과 DONE 상태를 동일한 종료 오류로 처리하는지 검증한다. */
    @Test
    @DisplayName("종료 시각이 지났거나 DONE이면 MT-009로 거절한다")
    void rejectsExpiredOrDoneMeeting() {
        /* 예약 종료보다 1초 늦은 KST 15:00:01 시계를 준비한다. */
        Clock lateClock = fixedClock("2026-08-06T06:00:01Z");
        MeetingEntryService expiredService = new MeetingEntryService(
                new RecordingMeetingEntryRepository(meeting(MeetingStatus.SCHEDULED, null)),
                lateClock
        );

        /* 아직 상태 전이가 안 됐더라도 입장 종료 시각을 지났으면 MT-009여야 한다. */
        assertErrorCode(() -> expiredService.enterMeeting(command(3L)), "MT-009");

        /* 입장 시간 안이어도 이미 DONE인 회의는 다시 시작할 수 없어야 한다. */
        MeetingEntryService doneService = new MeetingEntryService(
                new RecordingMeetingEntryRepository(meeting(
                        MeetingStatus.DONE,
                        LocalDateTime.of(2026, 8, 6, 13, 55)
                )),
                ENTRY_CLOCK
        );
        assertErrorCode(() -> doneService.enterMeeting(command(3L)), "MT-009");
    }

    /* 타 회사·미존재 회의를 회사 범위 잠금 조회에서 숨기는지 검증한다. */
    @Test
    @DisplayName("타 회사 또는 미존재 회의는 MT-001로 거절한다")
    void rejectsMissingMeetingInCompanyScope() {
        /* 잠금 조회가 빈 결과를 반환하는 MEET-07 서비스를 준비한다. */
        MeetingEntryService service = new MeetingEntryService(
                new RecordingMeetingEntryRepository(null),
                ENTRY_CLOCK
        );

        /* 정상 인증 형식이어도 회사 범위에서 회의를 찾지 못하면 MT-001이어야 한다. */
        assertErrorCode(() -> service.enterMeeting(command(3L)), "MT-001");
    }

    /* 잘못된 인증 식별자가 저장소 조회 전에 공통 입력 오류가 되는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 인증·회의 식별자는 Z-001로 거절한다")
    void rejectsInvalidIdentifiers() {
        /* 호출 여부를 확인할 수 있는 정상 회의 저장소 대역을 준비한다. */
        RecordingMeetingEntryRepository repository = new RecordingMeetingEntryRepository(
                meeting(MeetingStatus.SCHEDULED, null)
        );
        MeetingEntryService service = new MeetingEntryService(repository, ENTRY_CLOCK);

        /* 양수가 아닌 구성원 식별자는 DB 잠금 전에 Z-001로 거절돼야 한다. */
        EnterMeetingCommand invalidCommand = new EnterMeetingCommand(10L, 0L, 91L);
        assertErrorCode(() -> service.enterMeeting(invalidCommand), "Z-001");
        assertThat(repository.findCalls).isZero();
    }

    /* UTC 순간과 KST 지역으로 테스트용 고정 시계를 만든다. */
    private static Clock fixedClock(String instant) {
        /* 운영 Clock과 같은 Asia/Seoul 로컬 일시를 사용한다. */
        return Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"));
    }

    /* 요청자만 달리해 회사 10의 91번 회의 입장 명령을 만든다. */
    private EnterMeetingCommand command(Long requesterMemberId) {
        /* 회사와 회의 식별자는 정상값으로 고정한다. */
        return new EnterMeetingCommand(10L, requesterMemberId, 91L);
    }

    /* 상태와 실제 시작 시각을 지정한 완전한 회의 애그리거트를 만든다. */
    private Meeting meeting(MeetingStatus status, LocalDateTime startedAt) {
        /* 개설자 3번과 참석자 7·11번을 가진 14시~15시 회의를 복원한다. */
        return Meeting.reconstitute(
                91L,
                10L,
                12L,
                100L,
                2L,
                3L,
                "A커머스 온보딩 킥오프",
                status,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                true,
                305L,
                List.of(3L, 7L, 11L),
                startedAt,
                status == MeetingStatus.DONE ? LocalDateTime.of(2026, 8, 6, 14, 50) : null,
                LocalDateTime.of(2026, 8, 5, 9, 0),
                LocalDateTime.of(2026, 8, 5, 9, 0)
        );
    }

    /* 실행 결과가 예상 공개 오류 코드인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* BusinessException 타입과 ErrorCode 문자열을 함께 확인한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }

    /* MEET-07의 잠금 조회와 상태 저장 호출을 기록하는 저장소 대역이다. */
    private static final class RecordingMeetingEntryRepository implements MeetingEntryRepository {

        /* 잠금 조회에서 반환할 회의다. */
        private final Meeting meeting;

        /* 잠금 조회 호출 횟수다. */
        private int findCalls;

        /* 상태 저장 호출 횟수다. */
        private int saveCalls;

        /* 상태 저장에 전달된 회의다. */
        private Meeting savedMeeting;

        /* 테스트별 잠금 조회 결과로 저장소 대역을 만든다. */
        private RecordingMeetingEntryRepository(Meeting meeting) {
            /* null이면 회사 범위에서 회의를 찾지 못한 상황이다. */
            this.meeting = meeting;
        }

        /* 회사 범위 잠금 조회 호출을 기록하고 준비된 회의를 반환한다. */
        @Override
        public Optional<Meeting> findForEntry(Long companyId, Long meetingId) {
            /* 호출 횟수를 기록해 입력 검증이 DB보다 먼저인지 확인할 수 있게 한다. */
            findCalls++;
            return Optional.ofNullable(meeting);
        }

        /* 최초 입장 상태 저장을 기록하고 동일한 회의를 반환한다. */
        @Override
        public Meeting saveState(Meeting meeting) {
            /* 저장 횟수와 최종 상태를 테스트 검증용으로 보관한다. */
            saveCalls++;
            savedMeeting = meeting;
            return meeting;
        }
    }
}
