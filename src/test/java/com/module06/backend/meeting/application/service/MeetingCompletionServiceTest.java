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
import com.module06.backend.meeting.application.command.CompleteMeetingCommand;
import com.module06.backend.meeting.application.event.MeetingCompletionRequestedEvent;
import com.module06.backend.meeting.application.port.out.MeetingCompletionEventPublisher;
import com.module06.backend.meeting.application.result.MeetingCompletionResult;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.CaptureSessionControlRepository;
import com.module06.backend.meeting.domain.repository.MeetingCompletionRepository;

/* MEET-08 종료 순서·권한·상태·이벤트 계약을 검증하는 애플리케이션 서비스 테스트다. */
@DisplayName("MEET-08 회의 종료 서비스")
class MeetingCompletionServiceTest {

    /* 명세 예시 종료 시각인 2026-08-06 15:02:40 KST를 고정한다. */
    private static final Clock COMPLETION_CLOCK = Clock.fixed(
            Instant.parse("2026-08-06T06:02:40Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* host 요청이 캡처와 회의를 순서대로 종료하고 분석 이벤트를 한 번 발행하는지 검증한다. */
    @Test
    @DisplayName("host가 캡처 세션과 회의를 종료하고 PENDING 분석 이벤트를 발행한다")
    void completesMeetingAndPublishesAnalysisRequest() {
        /* 진행 중 회의와 ACTIVE 캡처 세션을 반환하는 저장소 대역을 준비한다. */
        RecordingCompletionRepository repository = new RecordingCompletionRepository(
                meeting(MeetingStatus.IN_PROGRESS),
                captureSession(CaptureSessionStatus.ACTIVE)
        );
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher(repository.calls);
        MeetingCompletionService service = service(repository, eventPublisher);

        /* 회의 개설자 3번이 정상 종료를 요청한다. */
        MeetingCompletionResult result = service.completeMeeting(command(3L, "MEMBER", false));

        /* D 소유 상태와 실제 시작·종료 시간 및 실측 64분을 반환해야 한다. */
        assertThat(result.meetingId()).isEqualTo(91L);
        assertThat(result.meetingStatus()).isEqualTo(MeetingStatus.DONE);
        assertThat(result.processingStatus()).isEqualTo("PENDING");
        assertThat(result.captureSessionStatus()).isEqualTo(CaptureSessionStatus.ENDED);
        assertThat(result.startedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 13, 58, 12));
        assertThat(result.endedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 15, 2, 40));
        assertThat(result.durationMinutes()).isEqualTo(64L);

        /* 계약한 저장·발행 순서는 회의 잠금, 세션 잠금·저장, 회의 저장, 이벤트 발행이어야 한다. */
        assertThat(repository.calls).containsExactly(
                "findMeeting",
                "findCaptureSession",
                "saveCaptureSession",
                "saveMeeting",
                "publishEvent"
        );

        /* 이벤트에는 A가 meetingId 멱등 처리와 세션 조립 판단에 쓸 식별자가 담겨야 한다. */
        assertThat(eventPublisher.event).isEqualTo(new MeetingCompletionRequestedEvent(
                10L,
                91L,
                15L,
                LocalDateTime.of(2026, 8, 6, 15, 2, 40)
        ));
    }

    /* OWNER·ADMIN이 자신이 개설하지 않은 회사 회의를 종료할 수 있는지 검증한다. */
    @Test
    @DisplayName("OWNER와 ADMIN은 비개설자여도 회사 회의를 종료할 수 있다")
    void allowsOwnerAndAdmin() {
        /* OWNER 역할 요청을 정상 저장소에 실행한다. */
        RecordingCompletionRepository ownerRepository = repository();
        MeetingCompletionResult ownerResult = service(
                ownerRepository,
                new RecordingEventPublisher(ownerRepository.calls)
        ).completeMeeting(command(99L, "OWNER", false));

        /* OWNER 역할만으로 host가 아닌 회의를 종료할 수 있어야 한다. */
        assertThat(ownerResult.meetingStatus()).isEqualTo(MeetingStatus.DONE);

        /* 관리자 플래그 요청도 별도 정상 저장소에 실행한다. */
        RecordingCompletionRepository adminRepository = repository();
        MeetingCompletionResult adminResult = service(
                adminRepository,
                new RecordingEventPublisher(adminRepository.calls)
        ).completeMeeting(command(99L, "MEMBER", true));

        /* ADMIN principal 플래그로도 동일한 종료 권한을 가져야 한다. */
        assertThat(adminResult.meetingStatus()).isEqualTo(MeetingStatus.DONE);
    }

    /* 일반 비개설자 요청이 상태 저장과 이벤트 발행 전에 차단되는지 검증한다. */
    @Test
    @DisplayName("host·OWNER·ADMIN이 아니면 MT-006으로 거절한다")
    void rejectsUnauthorizedMember() {
        /* 정상 회의를 조회하지만 요청자가 참석자 7번인 상황을 준비한다. */
        RecordingCompletionRepository repository = repository();
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher(repository.calls);
        MeetingCompletionService service = service(repository, eventPublisher);

        /* 일반 참석자는 남의 회의를 종료할 수 없어야 한다. */
        assertErrorCode(
                () -> service.completeMeeting(command(7L, "MEMBER", false)),
                "MT-006"
        );
        assertThat(repository.calls).containsExactly("findMeeting");
        assertThat(eventPublisher.event).isNull();
    }

    /* 회사 범위 조회에서 회의를 찾지 못한 경우 존재를 숨기는지 검증한다. */
    @Test
    @DisplayName("타 회사 또는 미존재 회의는 MT-001로 거절한다")
    void rejectsMissingMeeting() {
        /* 회사 범위 잠금 조회가 빈 결과를 반환하도록 준비한다. */
        RecordingCompletionRepository repository = new RecordingCompletionRepository(
                null,
                captureSession(CaptureSessionStatus.ACTIVE)
        );
        MeetingCompletionService service = service(
                repository,
                new RecordingEventPublisher(repository.calls)
        );

        /* 인증 형식이 정상이어도 회의가 없으면 MT-001이어야 한다. */
        assertErrorCode(() -> service.completeMeeting(command(3L, "MEMBER", false)), "MT-001");
        assertThat(repository.calls).containsExactly("findMeeting");
    }

    /* DONE과 SCHEDULED 상태가 각 공개 충돌 오류로 구분되는지 검증한다. */
    @Test
    @DisplayName("DONE은 MT-009, SCHEDULED는 MT-013으로 거절한다")
    void rejectsInvalidMeetingStates() {
        /* 이미 완료된 회의의 중복 종료는 분석 이벤트 중복 방지를 위해 MT-009여야 한다. */
        RecordingCompletionRepository doneRepository = new RecordingCompletionRepository(
                meeting(MeetingStatus.DONE),
                captureSession(CaptureSessionStatus.ENDED)
        );
        assertErrorCode(
                () -> service(doneRepository, new RecordingEventPublisher(doneRepository.calls))
                        .completeMeeting(command(3L, "MEMBER", false)),
                "MT-009"
        );

        /* 한 번도 입장하지 않은 예약 회의는 종료로 건너뛸 수 없어 MT-013이어야 한다. */
        RecordingCompletionRepository scheduledRepository = new RecordingCompletionRepository(
                meeting(MeetingStatus.SCHEDULED),
                captureSession(CaptureSessionStatus.ACTIVE)
        );
        assertErrorCode(
                () -> service(scheduledRepository, new RecordingEventPublisher(scheduledRepository.calls))
                        .completeMeeting(command(3L, "MEMBER", false)),
                "MT-013"
        );

        /* 두 상태 모두 캡처 세션을 잠그거나 저장하기 전에 실패해야 한다. */
        assertThat(doneRepository.calls).containsExactly("findMeeting");
        assertThat(scheduledRepository.calls).containsExactly("findMeeting");
    }

    /* 캡처 세션 부재와 이미 종료 상태가 명시적 캡처 오류로 처리되는지 검증한다. */
    @Test
    @DisplayName("캡처 세션이 없거나 이미 ENDED이면 분석 이벤트를 발행하지 않는다")
    void rejectsMissingOrEndedCaptureSession() {
        /* 캡처 세션이 없는 진행 중 회의를 준비한다. */
        RecordingCompletionRepository missingRepository = new RecordingCompletionRepository(
                meeting(MeetingStatus.IN_PROGRESS),
                null
        );
        RecordingEventPublisher missingPublisher = new RecordingEventPublisher(missingRepository.calls);

        /* 응답의 ENDED 상태를 만들 수 없으므로 CS-001로 거절돼야 한다. */
        assertErrorCode(
                () -> service(missingRepository, missingPublisher)
                        .completeMeeting(command(3L, "MEMBER", false)),
                "CS-001"
        );
        assertThat(missingPublisher.event).isNull();

        /* 이미 종료된 세션은 회의만 다시 종료해 분석을 재발행하지 않도록 CS-006으로 막는다. */
        RecordingCompletionRepository endedRepository = new RecordingCompletionRepository(
                meeting(MeetingStatus.IN_PROGRESS),
                captureSession(CaptureSessionStatus.ENDED)
        );
        RecordingEventPublisher endedPublisher = new RecordingEventPublisher(endedRepository.calls);
        assertErrorCode(
                () -> service(endedRepository, endedPublisher)
                        .completeMeeting(command(3L, "MEMBER", false)),
                "CS-006"
        );
        assertThat(endedPublisher.event).isNull();
    }

    /* 유효하지 않은 인증·Path 식별자가 잠금 조회 전에 거절되는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 인증·회의 식별자는 Z-001로 거절한다")
    void rejectsInvalidCommandBeforeRepositoryCall() {
        /* 호출 기록이 가능한 정상 저장소와 서비스를 준비한다. */
        RecordingCompletionRepository repository = repository();
        MeetingCompletionService service = service(
                repository,
                new RecordingEventPublisher(repository.calls)
        );

        /* 양수가 아닌 회의 식별자는 저장소 잠금 전에 공통 입력 오류가 돼야 한다. */
        CompleteMeetingCommand invalidCommand = new CompleteMeetingCommand(
                10L,
                3L,
                "MEMBER",
                false,
                0L
        );
        assertErrorCode(() -> service.completeMeeting(invalidCommand), "Z-001");
        assertThat(repository.calls).isEmpty();
    }

    /* 정상 진행 중 회의와 ACTIVE 세션을 가진 저장소 대역을 만든다. */
    private RecordingCompletionRepository repository() {
        /* 각 권한 테스트가 서로 상태를 공유하지 않도록 새 도메인 객체를 반환한다. */
        return new RecordingCompletionRepository(
                meeting(MeetingStatus.IN_PROGRESS),
                captureSession(CaptureSessionStatus.ACTIVE)
        );
    }

    /* 준비된 저장소와 이벤트 발행기로 MEET-08 서비스를 만든다. */
    private MeetingCompletionService service(
            RecordingCompletionRepository repository,
            MeetingCompletionEventPublisher eventPublisher
    ) {
        /* 운영과 같은 KST 시계를 고정값으로 주입한다. */
        return new MeetingCompletionService(
                repository,
                repository,
                eventPublisher,
                COMPLETION_CLOCK
        );
    }

    /* 요청자 권한만 바꾼 회사 10의 91번 회의 종료 명령을 만든다. */
    private CompleteMeetingCommand command(Long memberId, String role, boolean admin) {
        /* 회사와 회의 식별자는 정상값으로 고정한다. */
        return new CompleteMeetingCommand(10L, memberId, role, admin, 91L);
    }

    /* 상태별 실제 시각을 가진 91번 회의 애그리거트를 복원한다. */
    private Meeting meeting(MeetingStatus status) {
        /* 진행·완료 상태에는 실제 시작 시각을, 완료 상태에는 종료 시각까지 채운다. */
        LocalDateTime startedAt = status == MeetingStatus.SCHEDULED
                ? null
                : LocalDateTime.of(2026, 8, 6, 13, 58, 12);
        LocalDateTime endedAt = status == MeetingStatus.DONE
                ? LocalDateTime.of(2026, 8, 6, 15, 0)
                : null;

        /* 서비스 권한과 결과 검증에 필요한 회사·host·참석자 원본을 복원한다. */
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
                endedAt,
                LocalDateTime.of(2026, 8, 5, 9, 0),
                LocalDateTime.of(2026, 8, 5, 9, 0)
        );
    }

    /* 상태별 15번 캡처 세션 애그리거트를 복원한다. */
    private CaptureSession captureSession(CaptureSessionStatus status) {
        /* ENDED 상태만 기존 종료 시각을 가지며 나머지는 제어 가능한 세션이다. */
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 6, 14, 0);
        LocalDateTime endedAt = status == CaptureSessionStatus.ENDED
                ? LocalDateTime.of(2026, 8, 6, 15, 0)
                : null;
        return CaptureSession.reconstitute(
                15L,
                91L,
                3L,
                status,
                startedAt,
                1_785_992_400_000L,
                null,
                endedAt,
                startedAt,
                endedAt == null ? startedAt : endedAt
        );
    }

    /* 실행 결과가 예상 공개 오류 코드인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* BusinessException 타입과 서비스 오류 코드 문자열을 함께 확인한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }

    /* 회의·캡처 저장 순서와 상태를 기록하는 두 도메인 저장소 대역이다. */
    private static final class RecordingCompletionRepository implements
            MeetingCompletionRepository,
            CaptureSessionControlRepository {

        /* 회사 범위 잠금 조회에서 반환할 회의다. */
        private final Meeting meeting;

        /* 캡처 세션 잠금 조회에서 반환할 세션이다. */
        private final CaptureSession captureSession;

        /* 조회·저장·발행 순서를 기록하는 목록이다. */
        private final List<String> calls = new ArrayList<>();

        /* 테스트별 회의와 캡처 상태로 저장소 대역을 만든다. */
        private RecordingCompletionRepository(Meeting meeting, CaptureSession captureSession) {
            /* null 값은 해당 회사 회의 또는 캡처 세션 부재를 뜻한다. */
            this.meeting = meeting;
            this.captureSession = captureSession;
        }

        /* 회의 잠금 조회 호출을 기록하고 준비된 회의를 반환한다. */
        @Override
        public Optional<Meeting> findForCompletion(Long companyId, Long meetingId) {
            /* 상태 검증이 첫 번째 저장소 동작인지 확인할 수 있게 기록한다. */
            calls.add("findMeeting");
            return Optional.ofNullable(meeting);
        }

        /* 완료 회의 저장 호출을 기록하고 전달받은 새 상태를 반환한다. */
        @Override
        public Meeting saveCompleted(Meeting completedMeeting) {
            /* 캡처 저장보다 뒤인지 검증할 수 있게 저장 순서를 기록한다. */
            calls.add("saveMeeting");
            return completedMeeting;
        }

        /* MEET-08에서 사용하지 않는 CAP 제어용 회의 조회 계약이다. */
        @Override
        public Optional<Meeting> findMeetingForControl(Long companyId, Long meetingId) {
            /* 잘못된 구현이 별도 비잠금 조회를 호출하면 테스트를 즉시 실패시킨다. */
            throw new AssertionError("MEET-08은 별도 CAP 회의 조회를 호출하면 안 됩니다.");
        }

        /* 캡처 세션 잠금 조회 호출을 기록하고 준비된 세션을 반환한다. */
        @Override
        public Optional<CaptureSession> findByMeetingIdForUpdate(Long meetingId) {
            /* 상태 저장 전에 세션 행을 잠갔는지 확인할 수 있게 기록한다. */
            calls.add("findCaptureSession");
            return Optional.ofNullable(captureSession);
        }

        /* 완료 캡처 세션 저장 호출을 기록하고 전달받은 새 상태를 반환한다. */
        @Override
        public CaptureSession save(CaptureSession completedCaptureSession) {
            /* 회의 저장보다 먼저인지 검증할 수 있게 저장 순서를 기록한다. */
            calls.add("saveCaptureSession");
            return completedCaptureSession;
        }
    }

    /* 분석 요청 이벤트와 발행 순서를 기록하는 아웃바운드 Port 대역이다. */
    private static final class RecordingEventPublisher implements MeetingCompletionEventPublisher {

        /* 저장소와 공유해 전체 처리 순서를 기록하는 목록이다. */
        private final List<String> calls;

        /* 마지막으로 발행된 완료 요청 이벤트다. */
        private MeetingCompletionRequestedEvent event;

        /* 저장소 호출 목록을 공유하는 발행기 대역을 만든다. */
        private RecordingEventPublisher(List<String> calls) {
            /* 하나의 목록으로 저장과 발행의 상대적 순서를 확인한다. */
            this.calls = calls;
        }

        /* 이벤트 발행 호출과 전달된 계약 값을 기록한다. */
        @Override
        public void publish(MeetingCompletionRequestedEvent event) {
            /* 이벤트는 회의 저장 뒤 정확히 한 번 기록돼야 한다. */
            calls.add("publishEvent");
            this.event = event;
        }
    }
}
