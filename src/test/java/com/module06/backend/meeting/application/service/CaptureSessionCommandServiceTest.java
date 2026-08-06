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
                START_CLOCK
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

    /* 회의 입장 전에는 캡처 시간축을 시작할 수 없는지 검증한다. */
    @Test
    @DisplayName("SCHEDULED 회의는 MT-013으로 거절한다")
    void rejectsMeetingNotStarted() {
        /* 아직 아무도 입장하지 않은 SCHEDULED 회의를 준비한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.SCHEDULED),
                false
        );

        /* host 요청이어도 회의 시작 전이면 MT-013이어야 한다. */
        assertErrorCode(() -> service(repository).startCaptureSession(command(3L)), "MT-013");
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

    /* 이미 회의 세션이 있으면 두 번째 세션을 저장하지 않는지 검증한다. */
    @Test
    @DisplayName("기존 캡처 세션이 있으면 CS-002로 거절한다")
    void rejectsDuplicateCaptureSession() {
        /* 회의별 존재 조회가 true인 저장소 대역을 준비한다. */
        RecordingCaptureSessionRepository repository = new RecordingCaptureSessionRepository(
                meeting(MeetingStatus.IN_PROGRESS),
                true
        );

        /* host의 재호출도 CS-002가 되고 저장은 수행되지 않아야 한다. */
        assertErrorCode(() -> service(repository).startCaptureSession(command(3L)), "CS-002");
        assertThat(repository.saveCalls).isZero();
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
        /* 참석자 ID를 모두 정상 구성원으로 해석하는 B Port 대역을 사용한다. */
        return new CaptureSessionCommandService(repository, new RecordingMemberQueryPort(), START_CLOCK);
    }

    /* 요청자만 달리해 회사 10의 91번 회의 시작 명령을 만든다. */
    private StartCaptureSessionCommand command(Long requesterMemberId) {
        /* 회사와 회의 식별자는 정상값으로 고정한다. */
        return new StartCaptureSessionCommand(10L, requesterMemberId, 91L);
    }

    /* 상태를 지정한 91번 회의 애그리거트를 복원한다. */
    private Meeting meeting(MeetingStatus status) {
        /* host 3번과 참석자 7·11번이 있는 회의를 테스트 원본으로 사용한다. */
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

        /* 테스트별 회의와 중복 여부로 저장소 대역을 만든다. */
        private RecordingCaptureSessionRepository(Meeting meeting, boolean captureSessionExists) {
            /* null 회의는 회사 범위에서 대상을 찾지 못한 상황을 표현한다. */
            this.meeting = meeting;
            this.captureSessionExists = captureSessionExists;
        }

        /* 회사 범위 잠금 조회 호출을 기록하고 준비된 회의를 반환한다. */
        @Override
        public Optional<Meeting> findMeetingForStart(Long companyId, Long meetingId) {
            /* 입력 검증이 저장소보다 먼저인지 확인할 수 있게 호출 횟수를 기록한다. */
            findCalls++;
            return Optional.ofNullable(meeting);
        }

        /* 준비된 회의별 세션 존재 여부를 반환한다. */
        @Override
        public boolean existsByMeetingId(Long meetingId) {
            /* 중복 시나리오에서 save가 호출되지 않는지 분리해 검증한다. */
            return captureSessionExists;
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
}
