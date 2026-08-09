package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.command.CancelMeetingCommand;
import com.module06.backend.meeting.application.event.MeetingCanceledEvent;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingCancellationRepository;

/* MEET-06 취소 권한·상태·멱등성·슬롯 해제·이벤트 계약을 검증하는 서비스 테스트다. */
@DisplayName("MEET-06 회의 취소 서비스")
class MeetingCancellationServiceTest {

    /* 취소 상태와 이벤트가 사용할 2026-08-08 10:30 KST 서버 시각을 고정한다. */
    private static final Clock CANCELLATION_CLOCK = Clock.fixed(
            Instant.parse("2026-08-08T01:30:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* host 취소가 상태·슬롯·이벤트를 최초 한 번만 변경하는지 검증한다. */
    @Test
    @DisplayName("host는 예약 회의를 취소하고 슬롯 해제와 취소 이벤트를 한 번 실행한다")
    void hostCancelsScheduledMeeting() {
        /* SCHEDULED 회의를 반환하는 기록 가능한 저장소와 이벤트 목록을 준비한다. */
        RecordingCancellationRepository repository = new RecordingCancellationRepository(
                meeting(MeetingStatus.SCHEDULED)
        );
        List<MeetingCanceledEvent> events = new ArrayList<>();
        MeetingCancellationService service = service(repository, events);

        /* 개설자 3번이 91번 회의를 취소한다. */
        service.cancelMeeting(command(3L, "MEMBER", false));

        /* 저장소에는 CANCELED와 고정 서버 취소 시각이 전달돼야 한다. */
        assertThat(repository.savedMeeting.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(repository.savedMeeting.getCanceledAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 8, 10, 30));
        assertThat(repository.saveCount).isEqualTo(1);

        /* 최초 이벤트에는 F 알림이 사용할 회의·참석자·시간 정보가 담겨야 한다. */
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.meetingId()).isEqualTo(91L);
            assertThat(event.companyId()).isEqualTo(10L);
            assertThat(event.hostMemberId()).isEqualTo(3L);
            assertThat(event.attendeeMemberIds()).containsExactly(3L, 7L, 11L);
            assertThat(event.canceledAt()).isEqualTo(LocalDateTime.of(2026, 8, 8, 10, 30));
        });
    }

    /* OWNER·ADMIN 관리 권한이 비host 회의를 취소할 수 있는지 검증한다. */
    @Test
    @DisplayName("OWNER와 ADMIN은 비개설자여도 같은 회사 회의를 취소할 수 있다")
    void allowsOwnerAndAdmin() {
        /* 소문자 OWNER도 역할 정규화를 거쳐 취소할 수 있어야 한다. */
        RecordingCancellationRepository ownerRepository = repository(MeetingStatus.SCHEDULED);
        service(ownerRepository, new ArrayList<>()).cancelMeeting(command(99L, "owner", false));
        assertThat(ownerRepository.saveCount).isEqualTo(1);

        /* 관리자 플래그가 있는 MEMBER도 별도 정상 회의를 취소할 수 있어야 한다. */
        RecordingCancellationRepository adminRepository = repository(MeetingStatus.SCHEDULED);
        service(adminRepository, new ArrayList<>()).cancelMeeting(command(99L, "MEMBER", true));
        assertThat(adminRepository.saveCount).isEqualTo(1);
    }

    /* 일반 비host 요청이 저장과 이벤트 전에 차단되는지 검증한다. */
    @Test
    @DisplayName("host·OWNER·ADMIN이 아니면 MT-006으로 거절한다")
    void rejectsUnauthorizedMember() {
        /* 참석자지만 개설자가 아닌 7번 구성원의 요청을 준비한다. */
        RecordingCancellationRepository repository = repository(MeetingStatus.SCHEDULED);
        List<MeetingCanceledEvent> events = new ArrayList<>();

        /* 일반 참석자는 다른 사람이 개설한 회의를 취소할 수 없어야 한다. */
        assertErrorCode(
                () -> service(repository, events).cancelMeeting(command(7L, "MEMBER", false)),
                "MT-006"
        );
        assertThat(repository.saveCount).isZero();
        assertThat(events).isEmpty();
    }

    /* 회사 범위 조회 실패와 시작 이후 상태를 공개 오류로 구분하는지 검증한다. */
    @Test
    @DisplayName("미존재 회의는 MT-001, 시작·종료 회의는 MT-014로 거절한다")
    void rejectsMissingAndStartedMeetings() {
        /* 타 회사 또는 미존재 회의는 존재 여부를 MT-001 뒤에 숨겨야 한다. */
        RecordingCancellationRepository missing = new RecordingCancellationRepository(null);
        assertErrorCode(
                () -> service(missing, new ArrayList<>()).cancelMeeting(command(3L, "MEMBER", false)),
                "MT-001"
        );

        /* IN_PROGRESS와 DONE 모두 시작 후 취소 충돌 계약인 MT-014를 사용해야 한다. */
        for (MeetingStatus status : List.of(MeetingStatus.IN_PROGRESS, MeetingStatus.DONE)) {
            RecordingCancellationRepository repository = repository(status);
            assertErrorCode(
                    () -> service(repository, new ArrayList<>())
                            .cancelMeeting(command(3L, "MEMBER", false)),
                    "MT-014"
            );
            assertThat(repository.saveCount).isZero();
        }
    }

    /* 재취소가 최초 취소 이력과 알림을 보존하는 멱등 성공인지 검증한다. */
    @Test
    @DisplayName("이미 CANCELED인 회의의 재취소는 저장과 이벤트 없이 성공한다")
    void treatsRepeatedCancellationAsIdempotentSuccess() {
        /* 최초 취소 시각이 저장된 CANCELED 회의를 조회하도록 준비한다. */
        RecordingCancellationRepository repository = repository(MeetingStatus.CANCELED);
        List<MeetingCanceledEvent> events = new ArrayList<>();

        /* 같은 host의 재취소는 예외 없이 종료돼야 한다. */
        service(repository, events).cancelMeeting(command(3L, "MEMBER", false));

        /* 데이터베이스 갱신과 알림 중복 발행은 모두 없어야 한다. */
        assertThat(repository.saveCount).isZero();
        assertThat(events).isEmpty();
    }

    /* 잘못된 인증·Path 값이 저장소 잠금 전에 거절되는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 인증·회의 식별자는 Z-001로 거절한다")
    void rejectsInvalidCommandBeforeRepositoryCall() {
        /* 호출 횟수를 기록할 정상 저장소를 준비한다. */
        RecordingCancellationRepository repository = repository(MeetingStatus.SCHEDULED);
        CancelMeetingCommand invalid = new CancelMeetingCommand(10L, 3L, " ", false, 0L);

        /* 역할과 회의 식별자가 잘못된 명령은 저장소 조회 전에 공통 입력 오류가 돼야 한다. */
        assertErrorCode(
                () -> service(repository, new ArrayList<>()).cancelMeeting(invalid),
                "Z-001"
        );
        assertThat(repository.findCount).isZero();
    }

    /* 지정 상태 회의를 가진 기록 가능한 저장소를 만든다. */
    private RecordingCancellationRepository repository(MeetingStatus status) {
        /* 테스트마다 상태를 공유하지 않는 새 애그리거트를 준비한다. */
        return new RecordingCancellationRepository(meeting(status));
    }

    /* 저장소와 기록 이벤트 목록으로 실제 취소 서비스를 만든다. */
    private MeetingCancellationService service(
            RecordingCancellationRepository repository,
            List<MeetingCanceledEvent> events
    ) {
        /* 이벤트 Port는 목록 추가 함수로 대역하고 운영과 같은 KST 고정 시계를 주입한다. */
        return new MeetingCancellationService(repository, events::add, CANCELLATION_CLOCK);
    }

    /* 요청자 권한만 바꾼 회사 10의 91번 회의 취소 명령을 만든다. */
    private CancelMeetingCommand command(Long memberId, String role, boolean admin) {
        /* 회사와 회의 식별자는 정상값으로 고정한다. */
        return new CancelMeetingCommand(10L, memberId, role, admin, 91L);
    }

    /* 상태별 실제·취소 시각을 가진 회의 애그리거트를 복원한다. */
    private Meeting meeting(MeetingStatus status) {
        /* 시작·종료·취소 시각은 해당 상태에서만 존재하도록 준비한다. */
        LocalDateTime startedAt = status == MeetingStatus.IN_PROGRESS || status == MeetingStatus.DONE
                ? LocalDateTime.of(2026, 8, 8, 13, 58)
                : null;
        LocalDateTime endedAt = status == MeetingStatus.DONE
                ? LocalDateTime.of(2026, 8, 8, 15, 0)
                : null;
        LocalDateTime canceledAt = status == MeetingStatus.CANCELED
                ? LocalDateTime.of(2026, 8, 8, 9, 0)
                : null;

        /* 취소 이벤트와 권한 검증에 필요한 전체 회의 값을 사용한다. */
        return Meeting.reconstitute(
                91L,
                10L,
                12L,
                100L,
                2L,
                3L,
                "주간 회의",
                status,
                LocalDateTime.of(2026, 8, 8, 14, 0),
                LocalDateTime.of(2026, 8, 8, 15, 0),
                true,
                305L,
                List.of(3L, 7L, 11L),
                startedAt,
                endedAt,
                canceledAt,
                LocalDateTime.of(2026, 8, 7, 10, 0),
                LocalDateTime.of(2026, 8, 8, 9, 0)
        );
    }

    /* 실행 결과가 지정한 서비스 오류 코드인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* BusinessException의 외부 계약 코드를 추출해 기대값과 비교한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }

    /* 취소 잠금 조회와 상태·슬롯 저장 횟수를 기록하는 도메인 저장소 대역이다. */
    private static final class RecordingCancellationRepository implements MeetingCancellationRepository {

        /* 잠금 조회에서 반환할 선택 회의다. */
        private final Optional<Meeting> meeting;

        /* 잠금 조회 호출 횟수다. */
        private int findCount;

        /* 취소 상태·슬롯 해제 저장 호출 횟수다. */
        private int saveCount;

        /* 저장 경계로 전달된 취소 회의다. */
        private Meeting savedMeeting;

        /* null 회의를 빈 결과로 변환해 저장소 대역을 만든다. */
        private RecordingCancellationRepository(Meeting meeting) {
            /* Optional 자체는 null이 아니므로 준비한 조회 결과를 보관한다. */
            this.meeting = Optional.ofNullable(meeting);
        }

        /* 회사 범위 잠금 조회 호출을 기록하고 준비한 회의를 반환한다. */
        @Override
        public Optional<Meeting> findForCancellation(Long companyId, Long meetingId) {
            /* 서비스가 형식 검증을 통과한 뒤에만 호출했는지 확인할 수 있게 횟수를 늘린다. */
            findCount++;
            return meeting;
        }

        /* 취소 회의를 기록하고 슬롯까지 해제된 저장 결과처럼 그대로 반환한다. */
        @Override
        public Meeting saveCancellationAndReleaseSlots(Meeting meeting) {
            /* 멱등·오류 경로와 정상 경로를 구분할 저장 횟수와 인자를 기록한다. */
            saveCount++;
            savedMeeting = meeting;
            return meeting;
        }
    }
}
