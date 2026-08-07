package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.command.UpdateMeetingCommand;
import com.module06.backend.meeting.application.event.MeetingUpdatedEvent;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.result.MeetingUpdateResult;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingUpdateRepository;

/*
 * MEET-05 회의 수정 서비스의 권한·상태·부분 수정·예약 재점유 규칙을 검증한다.
 */
@DisplayName("MEET-05 회의 정보 수정 서비스")
class MeetingUpdateServiceTest {

    /* 시간과 회의실 변경이 슬롯 재점유와 수정 이벤트로 이어지는지 검증한다. */
    @Test
    @DisplayName("host는 예약 시간을 변경하고 슬롯을 재점유한다")
    void hostReschedulesMeetingAndReplacesSlots() {
        /* SCHEDULED 회의와 정상 외부 Port를 가진 서비스 Fixture를 준비한다. */
        Fixture fixture = new Fixture(meeting(MeetingStatus.SCHEDULED));

        /* host가 시작·종료를 한 시간 뒤로 변경한다. */
        MeetingUpdateResult result = fixture.service.updateMeeting(new UpdateMeetingCommand(
                10L,
                3L,
                "MEMBER",
                false,
                91L,
                false,
                null,
                false,
                null,
                false,
                null,
                true,
                LocalDateTime.of(2026, 8, 8, 15, 0),
                true,
                LocalDateTime.of(2026, 8, 8, 16, 0),
                false,
                null
        ));

        /* 변경된 예약 일시와 기존 회의실 표시 정보가 결과에 반영돼야 한다. */
        assertThat(result.startAt()).isEqualTo(LocalDateTime.of(2026, 8, 8, 15, 0));
        assertThat(result.endAt()).isEqualTo(LocalDateTime.of(2026, 8, 8, 16, 0));
        assertThat(result.meetingRoom().meetingRoomId()).isEqualTo(2L);

        /* 실제 예약 변경이므로 저장소는 슬롯 교체 플래그로 한 번 호출돼야 한다. */
        assertThat(fixture.repository.savedMeeting).isNotNull();
        assertThat(fixture.repository.reservationChanged).isTrue();
        assertThat(fixture.roomPort.activeLookupCount).isEqualTo(1);

        /* 커밋 후 알림 소비자가 사용할 최신 시간축 이벤트가 한 건 발행돼야 한다. */
        assertThat(fixture.events).singleElement().satisfies(event -> {
            assertThat(event.meetingId()).isEqualTo(91L);
            assertThat(event.startAt()).isEqualTo(LocalDateTime.of(2026, 8, 8, 15, 0));
            assertThat(event.attendeeMemberIds()).containsExactly(3L, 7L, 11L);
        });
    }

    /* OWNER가 슬롯을 건드리지 않는 메타 필드만 수정하는 경로를 검증한다. */
    @Test
    @DisplayName("OWNER는 제목·프로젝트·녹음 동의를 슬롯 교체 없이 수정한다")
    void ownerUpdatesMetadataWithoutReplacingSlots() {
        /* 일반 예약 회의를 반환하는 Fixture를 준비한다. */
        Fixture fixture = new Fixture(meeting(MeetingStatus.SCHEDULED));

        /* 비host OWNER가 제목·프로젝트·녹음 동의만 변경한다. */
        MeetingUpdateResult result = fixture.service.updateMeeting(new UpdateMeetingCommand(
                10L,
                99L,
                "OWNER",
                false,
                91L,
                true,
                " 변경된 주간 회의 ",
                true,
                13L,
                false,
                null,
                false,
                null,
                false,
                null,
                true,
                false
        ));

        /* 제목은 정규화되고 프로젝트·녹음 동의가 최종 도메인 상태에 반영돼야 한다. */
        assertThat(fixture.repository.savedMeeting.getTitle()).isEqualTo("변경된 주간 회의");
        assertThat(fixture.repository.savedMeeting.getProjectId()).isEqualTo(13L);
        assertThat(fixture.repository.savedMeeting.isRecordingConsent()).isFalse();

        /* 예약 값은 동일하므로 활성 회의실 잠금과 슬롯 교체를 실행하지 않아야 한다. */
        assertThat(fixture.repository.reservationChanged).isFalse();
        assertThat(fixture.roomPort.activeLookupCount).isZero();
        assertThat(fixture.roomPort.historicalLookupCount).isEqualTo(1);
        assertThat(fixture.projectPort.existsLookupCount).isEqualTo(1);
        assertThat(result.status()).isEqualTo(MeetingStatus.SCHEDULED);
    }

    /* 일반 비host 참석자가 관리 API를 호출하는 경우를 검증한다. */
    @Test
    @DisplayName("일반 비host 참석자의 수정은 MT-006으로 거절한다")
    void rejectsNonHostMember() {
        /* 참석자 7번이 포함된 회의를 반환하는 Fixture를 준비한다. */
        Fixture fixture = new Fixture(meeting(MeetingStatus.SCHEDULED));

        /* 참석자지만 host가 아닌 MEMBER의 제목 수정은 권한 오류여야 한다. */
        assertErrorCode(
                () -> fixture.service.updateMeeting(titleCommand(7L, "MEMBER", false, "변경 시도")),
                "MT-006"
        );

        /* 권한 검증 뒤에는 외부 Port·저장·이벤트가 실행되지 않아야 한다. */
        assertThat(fixture.repository.savedMeeting).isNull();
        assertThat(fixture.events).isEmpty();
    }

    /* 시작된 회의가 수정되지 않는 상태 규칙을 검증한다. */
    @Test
    @DisplayName("IN_PROGRESS 회의 수정은 MT-014로 거절한다")
    void rejectsStartedMeeting() {
        /* 실제 시작 시각을 가진 진행 중 회의를 반환하는 Fixture를 준비한다. */
        Fixture fixture = new Fixture(meeting(MeetingStatus.IN_PROGRESS));

        /* host 요청이어도 캡처 시간축이 시작된 회의는 수정할 수 없다. */
        assertErrorCode(
                () -> fixture.service.updateMeeting(titleCommand(3L, "MEMBER", false, "변경 시도")),
                "MT-014"
        );
    }

    /* 다른 회사 또는 존재하지 않는 회의의 테넌트 격리를 검증한다. */
    @Test
    @DisplayName("회사 범위에서 찾지 못한 회의는 MT-001로 숨긴다")
    void hidesMissingOrOtherCompanyMeeting() {
        /* 회사 조건 잠금 조회가 빈 결과를 반환하도록 Fixture 저장소를 설정한다. */
        Fixture fixture = new Fixture(null);

        /* 권한과 무관하게 존재 여부를 404 계약 뒤에 숨겨야 한다. */
        assertErrorCode(
                () -> fixture.service.updateMeeting(titleCommand(3L, "OWNER", false, "변경 시도")),
                "MT-001"
        );
    }

    /* 변경된 최종 시간이 MEET-01 시간 규칙을 다시 통과하는지 검증한다. */
    @Test
    @DisplayName("종료가 시작보다 빠른 예약 변경은 MT-003으로 거절한다")
    void rejectsInvalidFinalTimeRange() {
        /* 정상 예약 회의를 반환하는 Fixture를 준비한다. */
        Fixture fixture = new Fixture(meeting(MeetingStatus.SCHEDULED));

        /* 시작만 기존 종료 이후로 보내 최종 시간 범위를 역전시킨다. */
        UpdateMeetingCommand command = new UpdateMeetingCommand(
                10L,
                3L,
                "MEMBER",
                false,
                91L,
                false,
                null,
                false,
                null,
                false,
                null,
                true,
                LocalDateTime.of(2026, 8, 8, 16, 0),
                false,
                null,
                false,
                null
        );

        /* 부분 입력을 합친 최종값 기준으로 MT-003을 반환해야 한다. */
        assertErrorCode(() -> fixture.service.updateMeeting(command), "MT-003");
        assertThat(fixture.repository.savedMeeting).isNull();
    }

    /* 동일 값을 보낸 PATCH가 불필요한 UPDATE와 알림을 만들지 않는지 검증한다. */
    @Test
    @DisplayName("현재와 같은 값의 PATCH는 저장과 이벤트 없이 멱등 성공한다")
    void treatsSameValuePatchAsIdempotentSuccess() {
        /* 기존 제목이 주간 회의인 예약 회의를 준비한다. */
        Fixture fixture = new Fixture(meeting(MeetingStatus.SCHEDULED));

        /* 가장자리 공백을 제외하면 현재와 같은 제목을 host가 요청한다. */
        MeetingUpdateResult result = fixture.service.updateMeeting(
                titleCommand(3L, "MEMBER", false, " 주간 회의 ")
        );

        /* 정상 결과는 반환하되 데이터베이스 UPDATE와 알림 이벤트는 없어야 한다. */
        assertThat(result.meetingId()).isEqualTo(91L);
        assertThat(fixture.repository.savedMeeting).isNull();
        assertThat(fixture.events).isEmpty();
    }

    /* 제목만 수정하는 기본 Command를 만든다. */
    private UpdateMeetingCommand titleCommand(
            Long requesterMemberId,
            String requesterRole,
            boolean requesterAdmin,
            String title
    ) {
        /* 나머지 PATCH 필드는 미전달 상태로 고정한다. */
        return new UpdateMeetingCommand(
                10L,
                requesterMemberId,
                requesterRole,
                requesterAdmin,
                91L,
                true,
                title,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null
        );
    }

    /* 지정한 상태로 서비스 검증에 필요한 전체 회의 애그리거트를 복원한다. */
    private static Meeting meeting(MeetingStatus status) {
        /* 진행 상태인 경우에만 실제 시작 시각을 넣고 종료 시각은 아직 비워 둔다. */
        LocalDateTime startedAt = status == MeetingStatus.SCHEDULED
                ? null
                : LocalDateTime.of(2026, 8, 8, 13, 58);

        /* 명세 예시의 회사·프로젝트·회의실·host·참석자 값을 사용한다. */
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
                null,
                LocalDateTime.of(2026, 8, 7, 10, 0),
                LocalDateTime.of(2026, 8, 7, 10, 0)
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

    /* 서비스가 호출하는 네 경계를 기록 가능한 대역으로 묶는 테스트 Fixture다. */
    private static final class Fixture {

        /* 저장된 회의와 슬롯 교체 여부를 기록하는 회의 저장소 대역이다. */
        private final RecordingMeetingUpdateRepository repository;

        /* 활성·이력 회의실 조회 횟수를 기록하는 회의실 Port 대역이다. */
        private final RecordingMeetingRoomPort roomPort = new RecordingMeetingRoomPort();

        /* 프로젝트 활성 검증 횟수를 기록하는 프로젝트 Port 대역이다. */
        private final RecordingProjectPort projectPort = new RecordingProjectPort();

        /* 발행된 수정 이벤트를 순서대로 기록한다. */
        private final List<MeetingUpdatedEvent> events = new ArrayList<>();

        /* 실제 검증 대상 서비스다. */
        private final MeetingUpdateService service;

        /* 선택 회의와 정상 외부 경계를 사용해 서비스 Fixture를 만든다. */
        private Fixture(Meeting meeting) {
            /* null 회의는 회사 범위 조회 실패를 뜻하도록 Optional로 변환한다. */
            this.repository = new RecordingMeetingUpdateRepository(Optional.ofNullable(meeting));

            /* 2026-08-07 09:00 KST로 고정해 8월 8일 예약이 항상 미래가 되게 한다. */
            Clock clock = Clock.fixed(
                    Instant.parse("2026-08-07T00:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );

            /* 기록 가능한 네 경계를 실제 MEET-05 서비스에 주입한다. */
            this.service = new MeetingUpdateService(
                    repository,
                    roomPort,
                    projectPort,
                    events::add,
                    clock
            );
        }
    }

    /* 회사 범위 잠금 조회와 저장 인자를 기록하는 도메인 저장소 대역이다. */
    private static final class RecordingMeetingUpdateRepository implements MeetingUpdateRepository {

        /* 잠금 조회에서 반환할 선택 회의다. */
        private final Optional<Meeting> meeting;

        /* saveUpdate로 전달된 최종 회의다. */
        private Meeting savedMeeting;

        /* saveUpdate로 전달된 슬롯 교체 여부다. */
        private boolean reservationChanged;

        /* 조회 결과를 고정해 저장소 대역을 만든다. */
        private RecordingMeetingUpdateRepository(Optional<Meeting> meeting) {
            /* Optional 자체는 null이 아니므로 그대로 보관한다. */
            this.meeting = meeting;
        }

        /* 회사 범위 잠금 조회 결과를 반환한다. */
        @Override
        public Optional<Meeting> findForUpdate(Long companyId, Long meetingId) {
            /* 서비스의 후속 권한·상태 검증을 위해 준비한 회의를 반환한다. */
            return meeting;
        }

        /* 최종 회의와 슬롯 교체 여부를 기록하고 저장 결과처럼 그대로 반환한다. */
        @Override
        public Meeting saveUpdate(Meeting meeting, boolean reservationChanged) {
            /* 검증에 사용할 두 인자를 기록한다. */
            this.savedMeeting = meeting;
            this.reservationChanged = reservationChanged;
            return meeting;
        }
    }

    /* 활성 잠금 조회와 비활성 포함 표시 조회를 제공하는 회의실 Port 대역이다. */
    private static final class RecordingMeetingRoomPort implements MeetingRoomQueryPort {

        /* 예약 변경 경로의 활성 회의실 조회 횟수다. */
        private int activeLookupCount;

        /* 예약 불변 경로의 이력 회의실 조회 횟수다. */
        private int historicalLookupCount;

        /* 요청한 회의실을 운영 시간 09:00~18:00인 활성 스냅숏으로 반환한다. */
        @Override
        public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
            /* 잠금 조회가 실제 예약 변경에서만 호출되는지 확인하도록 횟수를 센다. */
            activeLookupCount++;
            return Optional.of(room(meetingRoomId));
        }

        /* 요청한 회의실의 비활성 여부와 무관한 표시 스냅숏을 반환한다. */
        @Override
        public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
            /* 메타만 변경하는 경로가 활성 잠금을 사용하지 않는지 확인하도록 횟수를 센다. */
            historicalLookupCount++;
            return meetingRoomIds.stream().map(this::room).toList();
        }

        /* 지정한 식별자의 정상 회의실 스냅숏을 만든다. */
        private MeetingRoomSnapshot room(Long meetingRoomId) {
            /* 서비스의 운영 시간 검증과 응답 이름 조립에 필요한 값을 모두 채운다. */
            return new MeetingRoomSnapshot(
                    meetingRoomId,
                    "회의실 " + meetingRoomId,
                    "박애관",
                    8,
                    LocalTime.of(9, 0),
                    LocalTime.of(18, 0)
            );
        }
    }

    /* 활성 프로젝트 존재 검증과 표시 조회 계약을 제공하는 C Port 대역이다. */
    private static final class RecordingProjectPort implements ProjectQueryPort {

        /* 활성 프로젝트 존재 검증 호출 횟수다. */
        private int existsLookupCount;

        /* 프로젝트 변경값을 항상 유효한 회사 프로젝트로 처리한다. */
        @Override
        public boolean existsActiveProject(Long companyId, Long projectId) {
            /* 실제 변경에서만 호출되는지 확인하도록 횟수를 기록한다. */
            existsLookupCount++;
            return true;
        }

        /* MEET-05 응답에는 프로젝트 표시값이 없으므로 이 조회는 사용하지 않는다. */
        @Override
        public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
            /* 잘못된 서비스 의존을 조기에 발견하기 위해 호출되면 테스트를 실패시킨다. */
            throw new AssertionError("MEET-05는 프로젝트 표시 조회를 호출하지 않습니다.");
        }
    }
}
