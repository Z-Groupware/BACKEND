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
import com.module06.backend.meeting.application.command.StartCaptureSessionCommand;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult.RosterType;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.CaptureSessionRepository;

/*
 * CAP-01 서비스의 테넌트·상태·host·중복 검증과 roster 생성을 단위 테스트한다.
 */
@DisplayName("CAP-01 캡처 세션 시작 서비스")
class CaptureSessionCommandServiceTest {

    /* 모든 시간 값이 같은 순간을 가리키도록 고정한 KST 서버 시계다. */
    private static final Clock START_CLOCK = Clock.fixed(
            Instant.parse("2026-08-06T05:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* 정상 host 요청이 ACTIVE 세션과 닫힌 roster를 만드는지 검증한다. */
    @Test
    @DisplayName("진행 중인 회의의 host가 ACTIVE 캡처 세션을 시작한다")
    void startsCaptureSessionForHost() {
        /* 진행 중인 회의를 반환하고 저장 호출을 기록하는 저장소 대역을 준비한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.IN_PROGRESS),
                false
        );
        RecordingMemberQueryPort memberQueryPort = new RecordingMemberQueryPort();
        CaptureSessionCommandService service = new CaptureSessionCommandService(
                repository,
                memberQueryPort,
                new CaptureSessionCreationService(repository, START_CLOCK)
        );

        /* 회사 10의 host 3번이 91번 회의 캡처 세션을 시작한다. */
        CaptureSessionStartResult result = service.startCaptureSession(command(3L));

        /* 데이터베이스 식별자와 D 소유 최초 상태·시작자·시간축이 명세와 일치해야 한다. */
        assertThat(result.captureSessionId()).isEqualTo(15L);
        assertThat(result.status()).isEqualTo(CaptureSessionStatus.ACTIVE);
        assertThat(result.isPaused()).isFalse();
        assertThat(result.startedBy()).isEqualTo(3L);
        assertThat(result.startedAtEpochMs()).isEqualTo(START_CLOCK.instant().toEpochMilli());

        /* 예약 참석자 순서를 유지하고 unknown_person sentinel을 마지막에 정확히 한 번 넣어야 한다. */
        assertThat(result.roster())
                .extracting(CaptureSessionStartResult.RosterEntry::personKey)
                .containsExactly("member:3", "member:7", "member:11", "unknown_person");
        assertThat(result.roster().get(0).name()).isEqualTo("구성원-3");
        assertThat(result.roster().get(3).memberId()).isNull();
        assertThat(result.roster().get(3).name()).isEqualTo("명단 외");
        assertThat(result.roster().get(3).type()).isEqualTo(RosterType.UNKNOWN);

        /* B Port에는 토큰 회사와 전체 참석자 ID를 한 번에 전달해야 한다. */
        assertThat(memberQueryPort.companyId).isEqualTo(10L);
        assertThat(memberQueryPort.memberIds).containsExactly(3L, 7L, 11L);

        /* 저장된 D 애그리거트에는 현재 녹음자가 아닌 startedBy와 시간 기준점만 있어야 한다. */
        assertThat(repository.savedCaptureSession.getMeetingId()).isEqualTo(91L);
        assertThat(repository.savedCaptureSession.getStartedBy()).isEqualTo(3L);
        assertThat(repository.savedCaptureSession.getStatus()).isEqualTo(CaptureSessionStatus.ACTIVE);
    }

    /* B 조회 중 참석자가 교체되면 오래된 roster를 저장하지 않고 최신 명단으로 재시도하는지 검증한다. */
    @Test
    @DisplayName("roster 조회 중 명단이 바뀌면 잠금 트랜잭션을 롤백하고 최신 명단으로 재시도한다")
    void retriesWhenAttendeeRosterChangesBeforeLock() {
        /* 첫 스냅샷은 3·7·11, 잠금 시점부터는 3·7·15인 경합 저장소를 준비한다. */
        ChangingRosterRepository repository = new ChangingRosterRepository(
                meeting(MeetingStatus.IN_PROGRESS, List.of(3L, 7L, 11L)),
                meeting(MeetingStatus.IN_PROGRESS, List.of(3L, 7L, 15L))
        );
        CaptureSessionCommandService service = new CaptureSessionCommandService(
                repository,
                new RecordingMemberQueryPort(),
                new CaptureSessionCreationService(repository, START_CLOCK)
        );

        /* host의 CAP-01 요청을 실행해 첫 명단 불일치 뒤 자동 재시도를 유도한다. */
        CaptureSessionStartResult result = service.startCaptureSession(command(3L));

        /* 응답은 오래된 11번이 아니라 잠금으로 확인한 최신 15번을 포함해야 한다. */
        assertThat(result.roster())
                .extracting(CaptureSessionStartResult.RosterEntry::personKey)
                .containsExactly("member:3", "member:7", "member:15", "unknown_person");

        /* 첫 경합은 저장 없이 롤백되고 두 번째 잠금에서 세션 하나만 저장돼야 한다. */
        assertThat(repository.lockCalls).isEqualTo(2);
        assertThat(repository.saveCalls).isEqualTo(1);
    }

    /* 회사 범위에서 회의를 찾지 못하면 존재 여부를 숨기는지 검증한다. */
    @Test
    @DisplayName("타 회사 또는 미존재 회의는 MT-001로 거절한다")
    void rejectsMissingMeetingInCompanyScope() {
        /* 회사 범위 잠금 조회가 빈 결과를 반환하는 서비스를 준비한다. */
        CaptureSessionCommandService service = service(
                new RecordingCaptureSessionRepository(null, false)
        );

        /* 정상 인증 형식이어도 조회 결과가 없으면 MT-001이어야 한다. */
        assertErrorCode(() -> service.startCaptureSession(command(3L)), "MT-001");
    }

    /* 실제 host가 아닌 요청자는 역할과 무관하게 캡처를 제어하지 못하는지 검증한다. */
    @Test
    @DisplayName("회의 개설자가 아닌 요청자는 CS-003으로 거절한다")
    void rejectsNonHostRequester() {
        /* 참석자이지만 host가 아닌 7번 구성원으로 시작 요청을 만든다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.IN_PROGRESS),
                false
        );

        /* host_member_id와 일치하지 않으면 저장 전에 CS-003이어야 한다. */
        assertErrorCode(() -> service(repository).startCaptureSession(command(7L)), "CS-003");
        assertThat(repository.saveCalls).isZero();
    }

    /* 예약 회의의 녹음 시작이 회의 상태와 캡처 시간축을 함께 시작하는지 검증한다. */
    @Test
    @DisplayName("SCHEDULED 회의는 IN_PROGRESS 전이와 ACTIVE 세션 생성을 함께 저장한다")
    void startsScheduledMeetingAndCaptureSessionAtomically() {
        /* 아직 아무도 입장하지 않은 SCHEDULED 회의를 준비한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.SCHEDULED),
                false
        );

        /* host의 CAP-01 요청만으로 회의 시작과 캡처 세션 생성을 실행한다. */
        CaptureSessionStartResult result = service(repository).startCaptureSession(command(3L));

        /* 회의와 캡처 세션은 같은 시각을 기준으로 시작돼야 한다. */
        assertThat(repository.savedMeetingState.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(repository.savedMeetingState.getStartedAt()).isEqualTo(LocalDateTime.ofInstant(
                START_CLOCK.instant(),
                START_CLOCK.getZone()
        ));
        assertThat(result.startedAtEpochMs()).isEqualTo(START_CLOCK.instant().toEpochMilli());
        assertThat(repository.saveCalls).isEqualTo(1);
    }

    /* 종료된 회의를 새 세션으로 되살리지 않는지 검증한다. */
    @Test
    @DisplayName("DONE 회의는 MT-009로 거절한다")
    void rejectsCompletedMeeting() {
        /* 종료 시각까지 가진 DONE 회의를 준비한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.DONE),
                false
        );

        /* 상태 역행을 막기 위해 새 캡처 세션 시작을 MT-009로 거절해야 한다. */
        assertErrorCode(() -> service(repository).startCaptureSession(command(3L)), "MT-009");
    }

    /* host 재접속이 기존 ACTIVE 세션과 최초 시간축을 그대로 돌려받는지 검증한다. */
    @Test
    @DisplayName("기존 ACTIVE 세션이 있으면 새 행 없이 같은 세션을 반환한다")
    void returnsExistingActiveCaptureSessionIdempotently() {
        /* 기존 ACTIVE 세션을 가진 IN_PROGRESS 회의 저장소 대역을 준비한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.IN_PROGRESS),
                true
        );

        /* host가 CAP-01을 다시 호출하면 최초 세션 ID와 시간축을 그대로 받아야 한다. */
        CaptureSessionStartResult result = service(repository).startCaptureSession(command(3L));

        /* 새 INSERT 없이 기존 식별자·상태·epoch가 응답에 유지돼야 한다. */
        assertThat(result.captureSessionId()).isEqualTo(15L);
        assertThat(result.status()).isEqualTo(CaptureSessionStatus.ACTIVE);
        assertThat(result.startedAtEpochMs()).isEqualTo(START_CLOCK.instant().toEpochMilli());
        assertThat(repository.saveCalls).isZero();
    }

    /* 예약 시작 허용 창보다 이른 CAP-01이 회의 상태와 세션을 만들지 않는지 검증한다. */
    @Test
    @DisplayName("예약 시작 10분 전보다 이른 녹음 시작은 MT-008로 거절한다")
    void rejectsCaptureStartBeforeAllowedWindow() {
        /* 14시 회의를 허용 경계보다 1초 이른 13시 49분 59초에 시작한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.SCHEDULED),
                false
        );

        /* 잠금 트랜잭션 안에서 기존 회의 시작 정책과 같은 MT-008을 반환해야 한다. */
        assertErrorCode(() -> service(
                repository,
                fixedClock(LocalDateTime.of(2026, 8, 6, 13, 49, 59))
        ).startCaptureSession(command(3L)), "MT-008");
        assertThat(repository.savedMeetingState).isNull();
        assertThat(repository.saveCalls).isZero();
    }

    /* 예약 시작 허용 경계 시각 자체는 CAP-01의 정상 입력인지 검증한다. */
    @Test
    @DisplayName("예약 시작 10분 전 경계에서는 녹음 시작을 허용한다")
    void allowsCaptureStartAtOpeningBoundary() {
        /* 14시 회의를 정확히 13시 50분에 시작한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.SCHEDULED),
                false
        );

        /* 포함 경계이므로 회의와 캡처 세션이 함께 시작돼야 한다. */
        CaptureSessionStartResult result = service(
                repository,
                fixedClock(LocalDateTime.of(2026, 8, 6, 13, 50))
        ).startCaptureSession(command(3L));
        assertThat(result.status()).isEqualTo(CaptureSessionStatus.ACTIVE);
        assertThat(repository.savedMeetingState.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
    }

    /* 예약 종료 시각 이후의 CAP-01이 늦은 새 세션 생성을 막는지 검증한다. */
    @Test
    @DisplayName("예약 종료 시각 이후의 녹음 시작은 MT-009로 거절한다")
    void rejectsCaptureStartAfterMeetingEnd() {
        /* 15시 종료 회의를 종료보다 1초 늦은 시각에 시작한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.SCHEDULED),
                false
        );

        /* 늦은 신규 시작은 종료된 회의와 같은 MT-009 공개 계약으로 거절한다. */
        assertErrorCode(() -> service(
                repository,
                fixedClock(LocalDateTime.of(2026, 8, 6, 15, 0, 1))
        ).startCaptureSession(command(3L)), "MT-009");
        assertThat(repository.savedMeetingState).isNull();
        assertThat(repository.saveCalls).isZero();
    }

    /* 예약 종료 경계 시각 자체는 CAP-01의 정상 입력인지 검증한다. */
    @Test
    @DisplayName("예약 종료 시각 경계에서는 녹음 시작을 허용한다")
    void allowsCaptureStartAtClosingBoundary() {
        /* 15시 종료 회의를 정확히 종료 경계에서 시작한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.SCHEDULED),
                false
        );

        /* 포함 경계이므로 회의와 세션이 함께 시작돼야 한다. */
        CaptureSessionStartResult result = service(
                repository,
                fixedClock(LocalDateTime.of(2026, 8, 6, 15, 0))
        ).startCaptureSession(command(3L));
        assertThat(result.status()).isEqualTo(CaptureSessionStatus.ACTIVE);
        assertThat(repository.savedMeetingState.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
    }

    /* 잘못된 인증 식별자를 데이터베이스 조회 전에 거절하는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 인증·회의 식별자는 Z-001로 거절한다")
    void rejectsInvalidIdentifiers() {
        /* 호출 여부를 확인할 수 있는 정상 저장소 대역을 준비한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.IN_PROGRESS),
                false
        );

        /* 양수가 아닌 구성원 ID는 회의 잠금 조회 전에 공통 입력 오류가 돼야 한다. */
        StartCaptureSessionCommand invalidCommand = new StartCaptureSessionCommand(10L, 0L, 91L);
        assertErrorCode(() -> service(repository).startCaptureSession(invalidCommand), "Z-001");
        assertThat(repository.findCalls).isZero();
    }

    /* 테스트별 저장소 대역과 정상 구성원 Port를 가진 서비스를 만든다. */
    private CaptureSessionCommandService service(RecordingCaptureSessionRepository repository) {
        /* 기본 CAP-01 테스트는 14시로 고정된 서버 시계를 사용한다. */
        return service(repository, START_CLOCK);
    }

    /* 테스트별 저장소와 시각 경계를 지정한 CAP-01 서비스를 만든다. */
    private CaptureSessionCommandService service(
            RecordingCaptureSessionRepository repository,
            Clock clock
    ) {
        /* 참석자 ID를 모두 정상 구성원으로 해석하는 B Port 대역을 사용한다. */
        return new CaptureSessionCommandService(
                repository,
                new RecordingMemberQueryPort(),
                new CaptureSessionCreationService(repository, clock)
        );
    }

    /* KST 로컬 일시를 동일 순간의 고정 Clock으로 변환한다. */
    private Clock fixedClock(LocalDateTime dateTime) {
        /* 운영과 같은 Asia/Seoul 시간대를 유지해 정책 경계를 재현한다. */
        return Clock.fixed(dateTime.atZone(START_CLOCK.getZone()).toInstant(), START_CLOCK.getZone());
    }

    /* 요청자만 달리해 회사 10의 91번 회의 시작 명령을 만든다. */
    private StartCaptureSessionCommand command(Long requesterMemberId) {
        /* 회사와 회의 식별자는 정상값으로 고정한다. */
        return new StartCaptureSessionCommand(10L, requesterMemberId, 91L);
    }

    /* 상태를 지정한 91번 회의 애그리거트를 복원한다. */
    private Meeting meeting(MeetingStatus status) {
        /* 기본 테스트는 host 3번과 참석자 7·11번 명단을 사용한다. */
        return meeting(status, List.of(3L, 7L, 11L));
    }

    /* 상태와 참석자 목록을 지정한 91번 회의 애그리거트를 복원한다. */
    private Meeting meeting(MeetingStatus status, List<Long> attendeeMemberIds) {
        /* host 3번을 포함한 전달 명단으로 상태별 회의 원본을 만든다. */
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
                attendeeMemberIds,
                status == MeetingStatus.SCHEDULED ? null : LocalDateTime.of(2026, 8, 6, 13, 58),
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

    /* CAP-01 저장소 호출과 저장 대상을 기록하는 테스트 대역이다. */
    private static final class RecordingCaptureSessionRepository implements CaptureSessionRepository {

        /* 회사 범위 잠금 조회에서 반환할 회의다. */
        private final Meeting meeting;

        /* 기존 캡처 세션 존재 여부다. */
        private final boolean captureSessionExists;

        /* 회의 잠금 조회 호출 횟수다. */
        private int findCalls;

        /* 캡처 세션 저장 호출 횟수다. */
        private int saveCalls;

        /* 저장에 전달된 캡처 세션 애그리거트다. */
        private CaptureSession savedCaptureSession;

        /* 같은 트랜잭션에서 IN_PROGRESS로 저장된 회의 애그리거트다. */
        private Meeting savedMeetingState;

        /* 테스트별 회의와 중복 여부로 저장소 대역을 만든다. */
        private RecordingCaptureSessionRepository(Meeting meeting, boolean captureSessionExists) {
            /* null 회의는 회사 범위에서 대상을 찾지 못한 상황을 표현한다. */
            this.meeting = meeting;
            this.captureSessionExists = captureSessionExists;
        }

        /* B 조회용 회사 범위 스냅샷 호출을 기록하고 준비된 회의를 반환한다. */
        @Override
        public Optional<Meeting> findMeeting(Long companyId, Long meetingId) {
            /* 입력 검증이 저장소보다 먼저인지 확인할 수 있게 사전 조회 횟수를 기록한다. */
            findCalls++;
            return Optional.ofNullable(meeting);
        }

        /* 짧은 저장 트랜잭션의 회사 범위 잠금 조회 결과로 같은 회의를 반환한다. */
        @Override
        public Optional<Meeting> findMeetingForStart(Long companyId, Long meetingId) {
            /* 단위 테스트에서는 사전 스냅샷과 잠금 시점의 명단이 같은 정상 흐름을 사용한다. */
            return Optional.ofNullable(meeting);
        }

        /* 준비된 기존 ACTIVE 세션을 CAP-01 멱등 조회 결과로 반환한다. */
        @Override
        public Optional<CaptureSession> findByMeetingId(Long meetingId) {
            /* 중복 플래그가 없으면 신규 저장 흐름을 사용한다. */
            if (!captureSessionExists) {
                return Optional.empty();
            }
            return Optional.of(existingActiveCaptureSession(meetingId));
        }

        /* CAP-01이 시작 상태로 전이한 회의를 기록하고 그대로 반환한다. */
        @Override
        public Meeting saveMeetingState(Meeting meeting) {
            /* 세션 저장 전에 동일 트랜잭션으로 전달된 회의 상태와 시작 시각을 보관한다. */
            savedMeetingState = meeting;
            return meeting;
        }

        /* 저장 호출을 기록하고 ID 15가 반영된 캡처 세션으로 복원한다. */
        @Override
        public CaptureSession save(CaptureSession captureSession) {
            /* 저장 전 애그리거트를 보관해 D 소유 필드를 검증할 수 있게 한다. */
            saveCalls++;
            savedCaptureSession = captureSession;
            return CaptureSession.reconstitute(
                    15L,
                    captureSession.getMeetingId(),
                    captureSession.getStartedBy(),
                    captureSession.getStatus(),
                    captureSession.getStartedAt(),
                    captureSession.getStartedAtEpochMs(),
                    captureSession.getPausedAt(),
                    captureSession.getEndedAt(),
                    captureSession.getCreatedAt(),
                    captureSession.getUpdatedAt()
            );
        }
    }

    /* 요청 참석자를 같은 순서의 정상 구성원으로 해석하고 호출 인자를 기록하는 B Port 대역이다. */
    private static final class RecordingMemberQueryPort implements MemberQueryPort {

        /* 조회에 전달된 회사 식별자다. */
        private Long companyId;

        /* 조회에 전달된 참석자 식별자 목록이다. */
        private List<Long> memberIds = new ArrayList<>();

        /* 모든 요청 ID를 표시 이름이 있는 정상 구성원으로 변환한다. */
        @Override
        public List<MemberSnapshot> findActiveMembers(Long companyId, List<Long> memberIds) {
            /* 전달 인자를 복사해 배치 조회 계약을 검증할 수 있게 한다. */
            this.companyId = companyId;
            this.memberIds = List.copyOf(memberIds);
            return memberIds.stream()
                    .map(memberId -> new MemberSnapshot(
                            memberId,
                            "구성원-" + memberId,
                            100L,
                            "플랫폼팀"
                    ))
                    .toList();
        }
    }

    /* 첫 스냅샷과 잠금 시점의 참석자 명단을 다르게 반환해 MEET-09 경합을 재현하는 저장소다. */
    private static final class ChangingRosterRepository implements CaptureSessionRepository {

        /* B 이름 조회에 처음 사용될 과거 참석자 스냅샷이다. */
        private final Meeting initialMeeting;

        /* 잠금 시점과 재시도에서 사용될 최신 참석자 스냅샷이다. */
        private final Meeting updatedMeeting;

        /* 잠금 조회 호출 횟수다. */
        private int lockCalls;

        /* 실제 캡처 세션 저장 호출 횟수다. */
        private int saveCalls;

        /* 서로 다른 최초·최신 명단으로 경합 저장소를 만든다. */
        private ChangingRosterRepository(Meeting initialMeeting, Meeting updatedMeeting) {
            /* 첫 비잠금 조회 뒤에는 최신 명단만 보이도록 두 스냅샷을 보관한다. */
            this.initialMeeting = initialMeeting;
            this.updatedMeeting = updatedMeeting;
        }

        /* 첫 호출만 과거 명단을 주고 자동 재시도부터 최신 명단을 반환한다. */
        @Override
        public Optional<Meeting> findMeeting(Long companyId, Long meetingId) {
            /* 아직 잠금 시도가 없으면 과거 스냅샷이고 이후에는 최신 스냅샷이다. */
            return Optional.of(lockCalls == 0 ? initialMeeting : updatedMeeting);
        }

        /* 모든 잠금 조회에서 최신 명단을 반환해 첫 시도만 명단 불일치를 발생시킨다. */
        @Override
        public Optional<Meeting> findMeetingForStart(Long companyId, Long meetingId) {
            /* 재시도 횟수 검증을 위해 잠금 호출을 기록한다. */
            lockCalls++;
            return Optional.of(updatedMeeting);
        }

        /* 이 시나리오에는 기존 캡처 세션이 없다고 응답한다. */
        @Override
        public Optional<CaptureSession> findByMeetingId(Long meetingId) {
            /* 명단 경합만 검증하도록 중복 세션 분기를 비활성화한다. */
            return Optional.empty();
        }

        /* 이 경합 테스트는 이미 진행 중인 회의를 사용하므로 상태 저장 호출을 그대로 반환한다. */
        @Override
        public Meeting saveMeetingState(Meeting meeting) {
            /* 예기치 않은 SCHEDULED 입력이 생겨도 테스트 대역이 저장 계약을 충족하도록 한다. */
            return meeting;
        }

        /* 두 번째 시도의 캡처 세션을 저장하고 ID가 반영된 도메인으로 반환한다. */
        @Override
        public CaptureSession save(CaptureSession captureSession) {
            /* 첫 명단 불일치에서는 이 메서드가 호출되지 않아야 한다. */
            saveCalls++;
            return CaptureSession.reconstitute(
                    15L,
                    captureSession.getMeetingId(),
                    captureSession.getStartedBy(),
                    captureSession.getStatus(),
                    captureSession.getStartedAt(),
                    captureSession.getStartedAtEpochMs(),
                    captureSession.getPausedAt(),
                    captureSession.getEndedAt(),
                    captureSession.getCreatedAt(),
                    captureSession.getUpdatedAt()
            );
        }
    }

    /* CAP-01 재호출 테스트에서 반환할 기존 ACTIVE 세션을 만든다. */
    private static CaptureSession existingActiveCaptureSession(Long meetingId) {
        /* 최초 세션의 ID와 시간축은 재호출에서도 바뀌지 않는 고정 원본이다. */
        LocalDateTime startedAt = LocalDateTime.ofInstant(START_CLOCK.instant(), START_CLOCK.getZone());
        return CaptureSession.reconstitute(
                15L,
                meetingId,
                3L,
                CaptureSessionStatus.ACTIVE,
                startedAt,
                START_CLOCK.instant().toEpochMilli(),
                null,
                null,
                startedAt,
                startedAt
        );
    }
}
