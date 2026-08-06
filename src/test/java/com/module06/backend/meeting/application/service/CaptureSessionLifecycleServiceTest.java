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
import com.module06.backend.meeting.application.command.PauseCaptureSessionCommand;
import com.module06.backend.meeting.application.result.CaptureSessionPauseResult;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.CaptureSessionControlRepository;

/*
 * CAP-02 서비스의 회사 범위·host·세션 상태 검증과 PAUSED 전이를 단위 테스트한다.
 */
@DisplayName("CAP-02 캡처 일시정지 서비스")
class CaptureSessionLifecycleServiceTest {

    /* 명세 예시 pausedAt과 동일한 KST 순간으로 고정한 서버 시계다. */
    private static final Clock PAUSE_CLOCK = Clock.fixed(
            Instant.parse("2026-08-06T05:31:08Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* host의 정상 요청이 ACTIVE 세션을 PAUSED로 전이하는지 검증한다. */
    @Test
    @DisplayName("host가 ACTIVE 캡처 세션을 PAUSED로 전이한다")
    void pausesActiveCaptureSession() {
        /* 진행 중 회의와 ACTIVE 세션을 반환하는 저장소 대역을 준비한다. */
        RecordingControlRepository repository = new RecordingControlRepository(
                meeting(),
                captureSession(CaptureSessionStatus.ACTIVE)
        );
        CaptureSessionLifecycleService service = new CaptureSessionLifecycleService(
                repository,
                PAUSE_CLOCK
        );

        /* 회사 10의 host 3번이 91번 회의의 캡처 일시정지를 요청한다. */
        CaptureSessionPauseResult result = service.pauseCaptureSession(command(3L));

        /* 같은 세션 ID가 PAUSED 상태와 고정 시각으로 반환돼야 한다. */
        assertThat(result.captureSessionId()).isEqualTo(15L);
        assertThat(result.status()).isEqualTo(CaptureSessionStatus.PAUSED);
        assertThat(result.isPaused()).isTrue();
        assertThat(result.pausedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 14, 31, 8));

        /* 잠금 조회 뒤 상태 전이된 세션 하나만 저장돼야 한다. */
        assertThat(repository.lockCalls).isEqualTo(1);
        assertThat(repository.saveCalls).isEqualTo(1);
        assertThat(repository.savedSession.getStatus()).isEqualTo(CaptureSessionStatus.PAUSED);
    }

    /* 타 회사·미존재 회의와 세션 미생성을 같은 CS-001로 처리하는지 검증한다. */
    @Test
    @DisplayName("회의 또는 캡처 세션이 없으면 CS-001로 거절한다")
    void rejectsMissingMeetingOrCaptureSession() {
        /* 회사 범위에서 회의를 찾지 못하는 서비스를 준비한다. */
        CaptureSessionLifecycleService missingMeetingService = new CaptureSessionLifecycleService(
                new RecordingControlRepository(null, captureSession(CaptureSessionStatus.ACTIVE)),
                PAUSE_CLOCK
        );
        assertErrorCode(
                () -> missingMeetingService.pauseCaptureSession(command(3L)),
                "CS-001"
        );

        /* 회의는 있지만 아직 CAP-01이 실행되지 않은 서비스를 준비한다. */
        CaptureSessionLifecycleService missingSessionService = new CaptureSessionLifecycleService(
                new RecordingControlRepository(meeting(), null),
                PAUSE_CLOCK
        );
        assertErrorCode(
                () -> missingSessionService.pauseCaptureSession(command(3L)),
                "CS-001"
        );
    }

    /* 실제 host가 아닌 사용자의 상태 제어를 세션 조회 전에 거절하는지 검증한다. */
    @Test
    @DisplayName("회의 개설자가 아닌 요청자는 CS-003으로 거절한다")
    void rejectsNonHostRequester() {
        /* 참석자이지만 host가 아닌 7번 구성원의 요청을 실행한다. */
        RecordingControlRepository repository = new RecordingControlRepository(
                meeting(),
                captureSession(CaptureSessionStatus.ACTIVE)
        );
        CaptureSessionLifecycleService service = new CaptureSessionLifecycleService(repository, PAUSE_CLOCK);

        /* host_member_id와 다르면 세션 잠금 없이 CS-003이어야 한다. */
        assertErrorCode(() -> service.pauseCaptureSession(command(7L)), "CS-003");
        assertThat(repository.lockCalls).isZero();
    }

    /* PAUSED와 ENDED 상태의 재전이를 명세 오류로 구분하는지 검증한다. */
    @Test
    @DisplayName("PAUSED는 CS-004, ENDED는 CS-006으로 거절한다")
    void rejectsPausedAndEndedSessions() {
        /* 이미 일시정지된 세션은 CS-004로 응답해야 한다. */
        CaptureSessionLifecycleService pausedService = new CaptureSessionLifecycleService(
                new RecordingControlRepository(meeting(), captureSession(CaptureSessionStatus.PAUSED)),
                PAUSE_CLOCK
        );
        assertErrorCode(() -> pausedService.pauseCaptureSession(command(3L)), "CS-004");

        /* 종료된 세션은 PAUSED로 되돌리지 않고 CS-006으로 응답해야 한다. */
        CaptureSessionLifecycleService endedService = new CaptureSessionLifecycleService(
                new RecordingControlRepository(meeting(), captureSession(CaptureSessionStatus.ENDED)),
                PAUSE_CLOCK
        );
        assertErrorCode(() -> endedService.pauseCaptureSession(command(3L)), "CS-006");
    }

    /* 잘못된 인증과 Path 값이 저장소 호출 전에 공통 오류가 되는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 인증·회의 식별자는 Z-001로 거절한다")
    void rejectsInvalidIdentifiers() {
        /* 호출 횟수를 확인할 정상 저장소 대역을 준비한다. */
        RecordingControlRepository repository = new RecordingControlRepository(
                meeting(),
                captureSession(CaptureSessionStatus.ACTIVE)
        );
        CaptureSessionLifecycleService service = new CaptureSessionLifecycleService(repository, PAUSE_CLOCK);

        /* 양수가 아닌 구성원 식별자는 회의 조회 전에 Z-001이어야 한다. */
        PauseCaptureSessionCommand invalidCommand = new PauseCaptureSessionCommand(10L, 0L, 91L);
        assertErrorCode(() -> service.pauseCaptureSession(invalidCommand), "Z-001");
        assertThat(repository.meetingFindCalls).isZero();
    }

    /* 요청자만 달리해 회사 10의 91번 회의 일시정지 명령을 만든다. */
    private PauseCaptureSessionCommand command(Long requesterMemberId) {
        /* 회사와 회의 식별자는 정상값으로 고정한다. */
        return new PauseCaptureSessionCommand(10L, requesterMemberId, 91L);
    }

    /* host 3번이 개설한 진행 중 회의의 제어용 스냅샷을 만든다. */
    private Meeting meeting() {
        /* CAP-02는 참석자 목록을 사용하지 않으므로 빈 목록으로 복원한다. */
        return Meeting.reconstitute(
                91L,
                10L,
                12L,
                100L,
                2L,
                3L,
                "A커머스 온보딩 킥오프",
                MeetingStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                true,
                null,
                List.of(),
                LocalDateTime.of(2026, 8, 6, 13, 58),
                null,
                LocalDateTime.of(2026, 8, 5, 9, 0),
                LocalDateTime.of(2026, 8, 5, 9, 0)
        );
    }

    /* 상태별 저장된 캡처 세션 애그리거트를 만든다. */
    private CaptureSession captureSession(CaptureSessionStatus status) {
        /* 상태에 맞춰 pausedAt과 endedAt을 채워 실제 저장 원본을 재현한다. */
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 6, 14, 0);
        LocalDateTime pausedAt = status == CaptureSessionStatus.ACTIVE
                ? null
                : LocalDateTime.of(2026, 8, 6, 14, 20);
        LocalDateTime endedAt = status == CaptureSessionStatus.ENDED
                ? LocalDateTime.of(2026, 8, 6, 14, 50)
                : null;
        return CaptureSession.reconstitute(
                15L,
                91L,
                3L,
                status,
                startedAt,
                1_785_992_400_000L,
                pausedAt,
                endedAt,
                startedAt,
                endedAt == null ? (pausedAt == null ? startedAt : pausedAt) : endedAt
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

    /* CAP-02의 회의 조회와 캡처 세션 잠금·저장 호출을 기록하는 테스트 대역이다. */
    private static final class RecordingControlRepository implements CaptureSessionControlRepository {

        /* 회사 범위 host 조회에서 반환할 회의다. */
        private final Meeting meeting;

        /* 잠금 조회에서 반환할 캡처 세션이다. */
        private final CaptureSession captureSession;

        /* 회의 조회 호출 횟수다. */
        private int meetingFindCalls;

        /* 캡처 세션 잠금 조회 호출 횟수다. */
        private int lockCalls;

        /* 상태 저장 호출 횟수다. */
        private int saveCalls;

        /* 저장에 전달된 PAUSED 세션이다. */
        private CaptureSession savedSession;

        /* 테스트별 회의와 캡처 세션으로 저장소 대역을 만든다. */
        private RecordingControlRepository(Meeting meeting, CaptureSession captureSession) {
            /* null 값은 각각 회의 또는 세션 미존재 상황을 표현한다. */
            this.meeting = meeting;
            this.captureSession = captureSession;
        }

        /* 회사 범위 회의 조회 호출을 기록하고 준비된 회의를 반환한다. */
        @Override
        public Optional<Meeting> findMeetingForControl(Long companyId, Long meetingId) {
            /* 입력 검증과 host 검사 순서를 확인할 수 있게 호출을 기록한다. */
            meetingFindCalls++;
            return Optional.ofNullable(meeting);
        }

        /* 세션 행 잠금 호출을 기록하고 준비된 상태 원본을 반환한다. */
        @Override
        public Optional<CaptureSession> findByMeetingIdForUpdate(Long meetingId) {
            /* host 검사가 잠금보다 먼저인지 검증할 수 있게 호출을 기록한다. */
            lockCalls++;
            return Optional.ofNullable(captureSession);
        }

        /* 상태 전이된 세션을 기록하고 그대로 반환한다. */
        @Override
        public CaptureSession save(CaptureSession captureSession) {
            /* 저장 횟수와 전달 상태를 서비스 검증에 사용한다. */
            saveCalls++;
            savedSession = captureSession;
            return captureSession;
        }
    }
}
