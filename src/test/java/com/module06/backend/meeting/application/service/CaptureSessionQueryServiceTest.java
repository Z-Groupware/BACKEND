package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.query.GetCaptureSessionQuery;
import com.module06.backend.meeting.application.result.CaptureSessionStateResult;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.CaptureSessionQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;

/*
 * CAP-10 서비스의 회사 범위·예약 참석자 권한과 캡처 세션 상태 조회를 단위 테스트한다.
 */
@DisplayName("CAP-10 현재 캡처 세션 조회 서비스")
class CaptureSessionQueryServiceTest {

    /* 예약 참석자가 PAUSED 세션의 상태와 전체 시간축을 조회하는지 검증한다. */
    @Test
    @DisplayName("예약 참석자가 PAUSED 캡처 세션을 조회한다")
    void getsPausedCaptureSessionForAttendee() {
        /* 참석자 7번이 포함된 회의와 PAUSED 세션을 반환하는 저장소 대역을 준비한다. */
        RecordingMeetingQueryRepository meetingRepository = new RecordingMeetingQueryRepository(meeting());
        RecordingCaptureSessionQueryRepository captureRepository =
                new RecordingCaptureSessionQueryRepository(captureSession(CaptureSessionStatus.PAUSED));
        CaptureSessionQueryService service = new CaptureSessionQueryService(
                meetingRepository,
                captureRepository
        );

        /* 회사 10의 참석자 7번이 91번 회의의 현재 캡처 세션을 조회한다. */
        CaptureSessionStateResult result = service.getCaptureSession(query(7L));

        /* 저장된 세션 ID·PAUSED 상태·공통 시간축과 pausedAt이 그대로 반환돼야 한다. */
        assertThat(result.captureSessionId()).isEqualTo(15L);
        assertThat(result.status()).isEqualTo(CaptureSessionStatus.PAUSED);
        assertThat(result.isPaused()).isTrue();
        assertThat(result.startedAtEpochMs()).isEqualTo(1_785_992_400_000L);
        assertThat(result.startedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 14, 0));
        assertThat(result.pausedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 14, 31, 8));
        assertThat(meetingRepository.findCalls).isEqualTo(1);
        assertThat(captureRepository.findCalls).isEqualTo(1);
    }

    /* host 조회와 ACTIVE·ENDED 상태의 원본 반환 계약을 함께 검증한다. */
    @Test
    @DisplayName("host가 ACTIVE와 ENDED 세션 상태를 원본 그대로 조회한다")
    void getsActiveAndEndedCaptureSessionForHost() {
        /* ACTIVE 세션 조회에서는 isPaused가 false이고 pausedAt이 없어야 한다. */
        CaptureSessionQueryService activeService = new CaptureSessionQueryService(
                new RecordingMeetingQueryRepository(meeting()),
                new RecordingCaptureSessionQueryRepository(captureSession(CaptureSessionStatus.ACTIVE))
        );
        CaptureSessionStateResult active = activeService.getCaptureSession(query(3L));
        assertThat(active.status()).isEqualTo(CaptureSessionStatus.ACTIVE);
        assertThat(active.isPaused()).isFalse();
        assertThat(active.pausedAt()).isNull();

        /* ENDED 세션도 과거 회의 화면 복구를 위해 저장된 상태 그대로 반환해야 한다. */
        CaptureSessionQueryService endedService = new CaptureSessionQueryService(
                new RecordingMeetingQueryRepository(meeting()),
                new RecordingCaptureSessionQueryRepository(captureSession(CaptureSessionStatus.ENDED))
        );
        CaptureSessionStateResult ended = endedService.getCaptureSession(query(3L));
        assertThat(ended.status()).isEqualTo(CaptureSessionStatus.ENDED);
        assertThat(ended.isPaused()).isFalse();
    }

    /* 회의·권한·캡처 세션 부재를 명세의 서로 다른 공개 오류로 처리하는지 검증한다. */
    @Test
    @DisplayName("회의는 MT-001, 비참석자는 MT-007, 세션 부재는 CS-001로 거절한다")
    void rejectsMissingMeetingNonAttendeeAndMissingSession() {
        /* 미존재 또는 타 회사 회의는 캡처 조회 없이 MT-001이어야 한다. */
        RecordingCaptureSessionQueryRepository unusedCaptureRepository =
                new RecordingCaptureSessionQueryRepository(captureSession(CaptureSessionStatus.ACTIVE));
        CaptureSessionQueryService missingMeetingService = new CaptureSessionQueryService(
                new RecordingMeetingQueryRepository(null),
                unusedCaptureRepository
        );
        assertErrorCode(() -> missingMeetingService.getCaptureSession(query(7L)), "MT-001");
        assertThat(unusedCaptureRepository.findCalls).isZero();

        /* 같은 회사 구성원이어도 host·예약 참석자가 아니면 MT-007로 차단해야 한다. */
        RecordingCaptureSessionQueryRepository forbiddenCaptureRepository =
                new RecordingCaptureSessionQueryRepository(captureSession(CaptureSessionStatus.ACTIVE));
        CaptureSessionQueryService forbiddenService = new CaptureSessionQueryService(
                new RecordingMeetingQueryRepository(meeting()),
                forbiddenCaptureRepository
        );
        assertErrorCode(() -> forbiddenService.getCaptureSession(query(99L)), "MT-007");
        assertThat(forbiddenCaptureRepository.findCalls).isZero();

        /* 조회 권한은 있지만 CAP-01이 실행되지 않은 회의는 CS-001이어야 한다. */
        CaptureSessionQueryService missingSessionService = new CaptureSessionQueryService(
                new RecordingMeetingQueryRepository(meeting()),
                new RecordingCaptureSessionQueryRepository(null)
        );
        assertErrorCode(() -> missingSessionService.getCaptureSession(query(7L)), "CS-001");
    }

    /* 잘못된 인증과 Path 값이 저장소 호출 전에 공통 오류가 되는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 인증·회의 식별자는 Z-001로 거절한다")
    void rejectsInvalidIdentifiers() {
        /* 저장소 호출 횟수를 확인할 정상 대역을 준비한다. */
        RecordingMeetingQueryRepository meetingRepository = new RecordingMeetingQueryRepository(meeting());
        CaptureSessionQueryService service = new CaptureSessionQueryService(
                meetingRepository,
                new RecordingCaptureSessionQueryRepository(captureSession(CaptureSessionStatus.ACTIVE))
        );

        /* null 조건과 양수가 아닌 구성원 식별자는 조회 전에 Z-001이어야 한다. */
        assertErrorCode(() -> service.getCaptureSession(null), "Z-001");
        GetCaptureSessionQuery invalidQuery = new GetCaptureSessionQuery(10L, 0L, 91L);
        assertErrorCode(() -> service.getCaptureSession(invalidQuery), "Z-001");
        assertThat(meetingRepository.findCalls).isZero();
    }

    /* 요청자만 달리해 회사 10의 91번 회의 현재 세션 조회 조건을 만든다. */
    private GetCaptureSessionQuery query(Long requesterMemberId) {
        /* 회사와 회의 식별자는 정상값으로 고정한다. */
        return new GetCaptureSessionQuery(10L, requesterMemberId, 91L);
    }

    /* host 3번과 참석자 7·11번을 가진 회사 10의 회의 읽기 모델을 만든다. */
    private MeetingQueryRepository.MeetingSnapshot meeting() {
        /* CAP-10 권한 판정에 필요한 회사·host·참석자와 최소 회의 메타를 채운다. */
        return new MeetingQueryRepository.MeetingSnapshot(
                91L,
                10L,
                12L,
                3L,
                "A커머스 온보딩 킥오프",
                MeetingStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                LocalDateTime.of(2026, 8, 6, 13, 58),
                null,
                List.of(3L, 7L, 11L)
        );
    }

    /* 상태별 저장된 캡처 세션 애그리거트를 만든다. */
    private CaptureSession captureSession(CaptureSessionStatus status) {
        /* PAUSED만 현재 일시정지 시각을 가지며 ENDED는 종료 시각을 별도로 가진다. */
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 6, 14, 0);
        LocalDateTime pausedAt = status == CaptureSessionStatus.PAUSED
                ? LocalDateTime.of(2026, 8, 6, 14, 31, 8)
                : null;
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

    /* CAP-10의 회사 범위 회의와 참석자 읽기 호출을 기록하는 저장소 대역이다. */
    private static final class RecordingMeetingQueryRepository implements MeetingQueryRepository {

        /* 단건 회의 조회에서 반환할 읽기 모델이다. */
        private final MeetingSnapshot meeting;

        /* 단건 회의 조회 호출 횟수다. */
        private int findCalls;

        /* 테스트별 회의 존재 여부로 저장소 대역을 만든다. */
        private RecordingMeetingQueryRepository(MeetingSnapshot meeting) {
            /* null은 미존재 또는 타 회사 회의 상황을 표현한다. */
            this.meeting = meeting;
        }

        /* 회사 범위 단건 회의 조회 호출을 기록하고 준비된 모델을 반환한다. */
        @Override
        public Optional<MeetingSnapshot> findMeeting(Long companyId, Long meetingId) {
            /* 입력 검증과 후속 캡처 조회 순서를 확인할 수 있게 호출을 기록한다. */
            findCalls++;
            return Optional.ofNullable(meeting);
        }

        /* CAP-10에서 사용하지 않는 프로젝트 회의 조회는 빈 목록을 반환한다. */
        @Override
        public List<ProjectMeetingSnapshot> findProjectMeetingsOrdered(Long companyId, Long projectId) {
            /* 테스트 대역의 미사용 경로는 외부 데이터 없이 닫는다. */
            return List.of();
        }

        /* 프로젝트별 회의 수 조회는 CAP-10 테스트에서 사용하지 않는다. */
        @Override
        public Map<Long, Long> countMeetingsByProjectIds(Long companyId, List<Long> projectIds) {
            /* 테스트 대역의 미사용 신규 배치 계약은 빈 집계로 닫는다. */
            return Map.of();
        }

        /* CAP-10에서 사용하지 않는 예정 회의 조회는 빈 목록을 반환한다. */
        @Override
        public List<UpcomingMeetingSnapshot> findUpcomingMeetings(
                Long companyId,
                Long memberId,
                LocalDateTime now,
                int limit
        ) {
            /* 테스트 대역의 미사용 경로는 외부 데이터 없이 닫는다. */
            return List.of();
        }

        /* CAP-10에서 사용하지 않는 회의 안건 조회는 빈 목록을 반환한다. */
        @Override
        public List<MeetingTopicSnapshot> findMeetingTopics(Long companyId, List<Long> meetingIds) {
            /* 테스트 대역의 미사용 경로는 외부 데이터 없이 닫는다. */
            return List.of();
        }

        /* CAP-10에서 사용하지 않는 배치 참석자 조회는 빈 목록을 반환한다. */
        @Override
        public List<MeetingAttendeeReference> findMeetingAttendees(Long companyId, List<Long> meetingIds) {
            /* 테스트 대역의 미사용 경로는 외부 데이터 없이 닫는다. */
            return List.of();
        }
    }

    /* CAP-10의 비잠금 현재 캡처 세션 조회 호출을 기록하는 저장소 대역이다. */
    private static final class RecordingCaptureSessionQueryRepository implements CaptureSessionQueryRepository {

        /* 단건 캡처 세션 조회에서 반환할 애그리거트다. */
        private final CaptureSession captureSession;

        /* 비잠금 캡처 세션 조회 호출 횟수다. */
        private int findCalls;

        /* 테스트별 캡처 세션 존재 여부로 저장소 대역을 만든다. */
        private RecordingCaptureSessionQueryRepository(CaptureSession captureSession) {
            /* null은 CAP-01 미실행으로 세션이 없는 상황을 표현한다. */
            this.captureSession = captureSession;
        }

        /* 회의별 캡처 세션 조회 호출을 기록하고 준비된 애그리거트를 반환한다. */
        @Override
        public Optional<CaptureSession> findByMeetingId(Long meetingId) {
            /* 권한 검증 이후에만 호출되는지 확인할 수 있게 횟수를 기록한다. */
            findCalls++;
            return Optional.ofNullable(captureSession);
        }
    }
}
