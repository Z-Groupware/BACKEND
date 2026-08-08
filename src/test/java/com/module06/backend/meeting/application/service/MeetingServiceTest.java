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
import com.module06.backend.meeting.application.command.CreateMeetingCommand;
import com.module06.backend.meeting.application.event.MeetingReservedEvent;
import com.module06.backend.meeting.application.event.MeetingAttendeesAddedEvent;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingEventPublisher;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort.MeetingRoomSnapshot;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort.ProjectSnapshot;
import com.module06.backend.meeting.application.result.MeetingCreationResult;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.repository.MeetingRepository;

/*
 * MEET-01 애플리케이션 서비스의 정상 예약과 주요 검증 분기를 확인하는 단위 테스트다.
 */
@DisplayName("MEET-01 회의 예약 서비스")
class MeetingServiceTest {

    /* 테스트의 과거·미래 판정을 고정하는 서울 시계다. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* 정상 요청이 저장되고 결과와 이벤트가 명세대로 만들어지는지 검증한다. */
    @Test
    @DisplayName("유효한 요청을 예약하고 개설자 포함 결과와 이벤트를 반환한다")
    void createsMeetingReservation() {
        /* 저장된 회의와 발행 이벤트를 확인할 수 있는 대역을 준비한다. */
        RecordingMeetingRepository repository = new RecordingMeetingRepository();
        RecordingMeetingEventPublisher eventPublisher = new RecordingMeetingEventPublisher();
        MeetingService service = service(repository, eventPublisher, activeRoom(), validMembers(), true, true);

        /* 개설자를 목록에서 생략한 정상 예약 요청을 실행한다. */
        MeetingCreationResult result = service.createMeeting(validCommand());

        /* 저장된 회의는 생성 ID와 SCHEDULED 상태를 가져야 한다. */
        assertThat(result.meetingId()).isEqualTo(91L);
        assertThat(result.status().name()).isEqualTo("SCHEDULED");
        assertThat(repository.savedMeeting).isNotNull();

        /* 개설자가 첫 번째 참석자로 자동 포함되고 표시 정보가 조립돼야 한다. */
        assertThat(result.host().memberId()).isEqualTo(3L);
        assertThat(result.attendees())
                .extracting(MeetingCreationResult.Attendee::memberId)
                .containsExactly(3L, 7L, 11L);

        /* 저장 성공 뒤 예약 완료 이벤트가 한 번 발행돼야 한다. */
        assertThat(eventPublisher.events).hasSize(1);
        assertThat(eventPublisher.events.get(0).meetingId()).isEqualTo(91L);
        assertThat(eventPublisher.events.get(0).attendeeMemberIds()).containsExactly(3L, 7L, 11L);
    }

    /* 종료가 시작보다 늦지 않은 요청이 MT-003으로 거절되는지 검증한다. */
    @Test
    @DisplayName("종료 시각이 시작 시각과 같으면 MT-003으로 거절한다")
    void rejectsInvalidTimeRange() {
        /* 시작과 종료가 같은 요청을 준비한다. */
        CreateMeetingCommand command = command(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 14, 0),
                List.of(7L, 11L)
        );

        /* 외부 포트를 호출하기 전에 시간 범위 오류가 반환돼야 한다. */
        assertErrorCode(() -> defaultService().createMeeting(command), "MT-003");
    }

    /* 30분 그리드에 맞지 않는 요청이 MT-005로 거절되는지 검증한다. */
    @Test
    @DisplayName("30분 그리드가 아닌 시작 시각은 MT-005로 거절한다")
    void rejectsNonGridTime() {
        /* 14시 10분에 시작하는 요청을 준비한다. */
        CreateMeetingCommand command = command(
                LocalDateTime.of(2026, 8, 6, 14, 10),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L, 11L)
        );

        /* 슬롯 PK와 맞지 않는 요청은 저장 전에 거절돼야 한다. */
        assertErrorCode(() -> defaultService().createMeeting(command), "MT-005");
    }

    /* 회의실 이용 가능 범위를 넘는 요청이 MT-004로 거절되는지 검증한다. */
    @Test
    @DisplayName("회의실 종료 시각을 넘는 예약은 MT-004로 거절한다")
    void rejectsReservationOutsideRoomHours() {
        /* 회의실 운영 종료 이후까지 이어지는 요청을 준비한다. */
        CreateMeetingCommand command = command(
                LocalDateTime.of(2026, 8, 6, 17, 30),
                LocalDateTime.of(2026, 8, 6, 18, 30),
                List.of(7L, 11L)
        );

        /* 활성 회의실이어도 이용 가능 시간 밖이면 거절돼야 한다. */
        assertErrorCode(() -> defaultService().createMeeting(command), "MT-004");
    }

    /* 조회된 구성원이 요청 명단보다 적으면 MT-010으로 거절되는지 검증한다. */
    @Test
    @DisplayName("타 회사 또는 삭제된 참석자가 있으면 MT-010으로 거절한다")
    void rejectsInvalidAttendee() {
        /* 요청한 11번 구성원을 반환하지 않는 구성원 포트를 준비한다. */
        List<MemberQueryPort.MemberSnapshot> incompleteMembers = List.of(
                new MemberQueryPort.MemberSnapshot(3L, "지우", 100L, "기획"),
                new MemberQueryPort.MemberSnapshot(7L, "이든", 200L, "개발")
        );
        MeetingService service = service(
                new RecordingMeetingRepository(),
                new RecordingMeetingEventPublisher(),
                activeRoom(),
                incompleteMembers,
                true,
                true
        );

        /* 일부 구성원이 누락된 배치 조회 결과는 명단 전체 오류로 처리돼야 한다. */
        assertErrorCode(() -> service.createMeeting(validCommand()), "MT-010");
    }

    /* C도메인이 관련 액션을 찾지 못한 경우 MEET-01 외부 계약이 AC-001인지 검증한다. */
    @Test
    @DisplayName("관련 액션이 존재하지 않으면 AC-001로 거절한다")
    void rejectsMissingRelatedAction() {
        /* 액션 존재 여부만 false이고 나머지 외부 리소스는 정상인 서비스를 준비한다. */
        RecordingMeetingRepository repository = new RecordingMeetingRepository();
        RecordingMeetingEventPublisher eventPublisher = new RecordingMeetingEventPublisher();
        MeetingService service = service(
                repository,
                eventPublisher,
                activeRoom(),
                validMembers(),
                true,
                false
        );

        /* relatedActionId가 있는 정상 형식 요청에서 액션 미존재 오류를 확인한다. */
        assertErrorCode(() -> service.createMeeting(validCommand()), "AC-001");

        /* 외부 참조 검증 실패 뒤에는 회의 저장이나 예약 이벤트가 발생하면 안 된다. */
        assertThat(repository.savedMeeting).isNull();
        assertThat(eventPublisher.events).isEmpty();
    }

    /* 액션을 선택하지 않은 회의가 ActionQueryPort 연동 전에도 진행 가능한지 검증한다. */
    @Test
    @DisplayName("관련 액션을 선택하지 않으면 액션 조회 없이 회의를 예약한다")
    void createsMeetingWithoutRelatedAction() {
        /* 호출되는 순간 실패하는 액션 Port를 포함해 나머지 정상 의존성을 직접 조립한다. */
        ActionQueryPort pendingActionPort = new ActionQueryPort() {
            /* 관련 액션이 없는 예약에서는 존재 확인이 호출되면 안 된다. */
            @Override
            public boolean existsAction(Long companyId, Long actionId) {
                throw new AssertionError("relatedActionId가 없으면 ActionQueryPort를 호출하면 안 됩니다.");
            }

            /* MEET-10 배치 조회는 회의 예약 경로에서 사용하지 않는다. */
            @Override
            public java.util.List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                    Long companyId,
                    java.util.List<Long> meetingIds
            ) {
                throw new AssertionError("회의 예약 경로에서 분배 대기 배치 조회를 호출하면 안 됩니다.");
            }
        };
        MeetingService service = serviceWithActionPort(pendingActionPort);

        /* 선택 입력인 relatedActionId만 null인 정상 회의 예약 명령을 준비한다. */
        CreateMeetingCommand command = new CreateMeetingCommand(
                10L,
                3L,
                100L,
                "A커머스 온보딩 킥오프",
                12L,
                2L,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                true,
                null,
                List.of(7L, 11L)
        );

        /* 액션 Port를 호출하지 않고 예약이 완료되며 관련 액션은 null로 유지돼야 한다. */
        MeetingCreationResult result = service.createMeeting(command);
        assertThat(result.meetingId()).isEqualTo(91L);
    }

    /* 정상 외부 리소스를 반환하는 기본 서비스를 생성한다. */
    private MeetingService defaultService() {
        /* 개별 검증 테스트에서는 저장 결과를 따로 확인하지 않는다. */
        return service(
                new RecordingMeetingRepository(),
                new RecordingMeetingEventPublisher(),
                activeRoom(),
                validMembers(),
                true,
                true
        );
    }

    /* 전달받은 액션 Port와 나머지 정상 대역으로 MEET-01 서비스를 조립한다. */
    private MeetingService serviceWithActionPort(ActionQueryPort actionQueryPort) {
        /* 단건 회의실 조회와 사용하지 않는 배치 조회를 구현한 정상 포트를 만든다. */
        MeetingRoomQueryPort roomPort = new MeetingRoomQueryPort() {
            /* 활성 회의실 단건 결과를 반환한다. */
            @Override
            public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
                /* 테스트의 정상 회의실 결과를 그대로 반환한다. */
                return activeRoom();
            }

            /* 이 테스트에서 사용하지 않는 배치 표시 조회는 빈 목록을 반환한다. */
            @Override
            public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
                /* MEET-03 전용 계약이므로 데이터베이스 조회를 흉내 내지 않는다. */
                return List.of();
            }
        };

        /* 활성 프로젝트 존재와 사용하지 않는 표시 조회를 구현한 정상 포트를 만든다. */
        ProjectQueryPort projectPort = new ProjectQueryPort() {
            /* 요청 프로젝트가 활성 상태라고 응답한다. */
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                /* 이 테스트는 액션 선택 여부에만 집중하므로 true를 반환한다. */
                return true;
            }

            /* 이 테스트에서 사용하지 않는 프로젝트 배치 조회는 빈 목록을 반환한다. */
            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                /* MEET-03 전용 계약이므로 데이터베이스 조회를 흉내 내지 않는다. */
                return List.of();
            }
        };

        /* 액션 Port 외에는 모두 정상값을 반환하는 실제 회의 서비스를 생성한다. */
        return new MeetingService(
                new RecordingMeetingRepository(),
                roomPort,
                projectPort,
                (companyId, memberIds) -> validMembers(),
                actionQueryPort,
                new RecordingMeetingEventPublisher(),
                FIXED_CLOCK
        );
    }

    /* 테스트 조건에 따라 모든 아웃바운드 포트를 조립한 서비스를 생성한다. */
    private MeetingService service(
            MeetingRepository repository,
            MeetingEventPublisher eventPublisher,
            Optional<MeetingRoomQueryPort.MeetingRoomSnapshot> room,
            List<MemberQueryPort.MemberSnapshot> members,
            boolean projectExists,
            boolean actionExists
    ) {
        /* 단건 회의실 조회 결과와 사용하지 않는 배치 계약을 구현한 포트 대역이다. */
        MeetingRoomQueryPort roomPort = new MeetingRoomQueryPort() {
            /* 테스트가 준비한 활성 회의실 단건 결과를 반환한다. */
            @Override
            public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
                /* MEET-01 검증에 사용할 Optional을 그대로 반환한다. */
                return room;
            }

            /* MEET-03 배치 조회는 이 서비스 테스트에서 사용하지 않는다. */
            @Override
            public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
                /* 호출되지 않는 별도 계약을 빈 목록으로 만족시킨다. */
                return List.of();
            }
        };

        /* 프로젝트 존재 결과와 사용하지 않는 표시 정보 배치 계약을 구현한 포트 대역이다. */
        ProjectQueryPort projectPort = new ProjectQueryPort() {
            /* 테스트에서 정한 프로젝트 존재 여부를 반환한다. */
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                /* MEET-01 검증 조건으로 전달받은 값을 그대로 반환한다. */
                return projectExists;
            }

            /* MEET-03 프로젝트 표시 조회는 이 서비스 테스트에서 사용하지 않는다. */
            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                /* 호출되지 않는 별도 계약을 빈 목록으로 만족시킨다. */
                return List.of();
            }
        };

        /* 테스트에서 준비한 활성 구성원 목록을 반환하는 배치 포트 대역이다. */
        MemberQueryPort memberPort = (companyId, memberIds) -> members;

        /* 테스트에서 정한 관련 액션 존재 결과를 반환하는 포트 대역이다. */
        ActionQueryPort actionPort = new ActionQueryPort() {
            /* 테스트가 정한 관련 액션 존재 결과를 그대로 반환한다. */
            @Override
            public boolean existsAction(Long companyId, Long actionId) {
                return actionExists;
            }

            /* MEET-10 배치 조회는 회의 예약 경로에서 사용하지 않는다. */
            @Override
            public java.util.List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                    Long companyId,
                    java.util.List<Long> meetingIds
            ) {
                return java.util.List.of();
            }
        };

        /* 고정 시계를 포함한 실제 서비스를 반환한다. */
        return new MeetingService(
                repository,
                roomPort,
                projectPort,
                memberPort,
                actionPort,
                eventPublisher,
                FIXED_CLOCK
        );
    }

    /* 이용 가능 시간이 09:00부터 18:00인 활성 회의실 조회 결과를 만든다. */
    private Optional<MeetingRoomQueryPort.MeetingRoomSnapshot> activeRoom() {
        /* 응답 조립에 필요한 표시 정보도 함께 제공한다. */
        return Optional.of(new MeetingRoomQueryPort.MeetingRoomSnapshot(
                2L,
                "회의실 B",
                "박애관 422호",
                8,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        ));
    }

    /* 개설자와 요청 참석자 전원을 포함한 활성 구성원 조회 결과를 만든다. */
    private List<MemberQueryPort.MemberSnapshot> validMembers() {
        /* 구성원 이름과 팀 이름은 성공 응답 조립에 사용된다. */
        return List.of(
                new MemberQueryPort.MemberSnapshot(3L, "지우", 100L, "기획"),
                new MemberQueryPort.MemberSnapshot(7L, "이든", 200L, "개발"),
                new MemberQueryPort.MemberSnapshot(11L, "하린", 300L, "디자인")
        );
    }

    /* 명세 예시와 같은 정상 회의 예약 명령을 만든다. */
    private CreateMeetingCommand validCommand() {
        /* 고정 현재 시각보다 하루 뒤인 한 시간 예약을 사용한다. */
        return command(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                List.of(7L, 11L)
        );
    }

    /* 시간과 참석자만 바꾼 회의 예약 명령을 만든다. */
    private CreateMeetingCommand command(
            LocalDateTime startAt,
            LocalDateTime endAt,
            List<Long> attendeeMemberIds
    ) {
        /* 나머지 값은 정상적인 명세 예시 값으로 고정한다. */
        return new CreateMeetingCommand(
                10L,
                3L,
                100L,
                "A커머스 온보딩 킥오프",
                12L,
                2L,
                startAt,
                endAt,
                true,
                305L,
                attendeeMemberIds
        );
    }

    /* 실행 결과가 특정 서비스 오류 코드의 BusinessException인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* 예외 타입과 외부 계약 코드가 모두 일치해야 한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }

    /* 저장 요청을 기록하고 데이터베이스 생성 값을 흉내 내는 회의 저장소 대역이다. */
    private static final class RecordingMeetingRepository implements MeetingRepository {

        /* 서비스가 저장을 요청한 신규 회의다. */
        private Meeting savedMeeting;

        /* 신규 회의를 기록하고 91번 식별자가 생성된 저장 결과를 반환한다. */
        @Override
        public Meeting saveReservation(Meeting meeting) {
            /* 저장 전 상태를 검증할 수 있도록 입력 회의를 기록한다. */
            this.savedMeeting = meeting;

            /* 실제 DB가 ID와 생성·수정 시각을 채운 것처럼 도메인을 복원한다. */
            LocalDateTime persistedAt = LocalDateTime.of(2026, 8, 5, 9, 0);
            return Meeting.reconstitute(
                    91L,
                    meeting.getCompanyId(),
                    meeting.getProjectId(),
                    meeting.getTeamId(),
                    meeting.getMeetingRoomId(),
                    meeting.getHostMemberId(),
                    meeting.getTitle(),
                    meeting.getStatus(),
                    meeting.getStartAt(),
                    meeting.getEndAt(),
                    meeting.isRecordingConsent(),
                    meeting.getRelatedActionId(),
                    meeting.getAttendeeMemberIds(),
                    null,
                    null,
                    persistedAt,
                    persistedAt
            );
        }

        /* MEET-09 참석자 교체는 MEET-01 서비스 테스트에서 사용하지 않는다. */
        @Override
        public void replaceAttendees(Long meetingId, List<Long> attendeeMemberIds) {
            /* 호출되지 않는 별도 쓰기 계약이므로 아무 상태도 변경하지 않는다. */
        }
    }

    /* 발행된 예약 완료 이벤트를 순서대로 기록하는 이벤트 포트 대역이다. */
    private static final class RecordingMeetingEventPublisher implements MeetingEventPublisher {

        /* 서비스가 발행한 이벤트 목록이다. */
        private final List<MeetingReservedEvent> events = new ArrayList<>();

        /* 전달된 이벤트를 검증용 목록에 추가한다. */
        @Override
        public void publish(MeetingReservedEvent event) {
            /* 실제 메시지 브로커 대신 메모리에 이벤트를 보관한다. */
            events.add(event);
        }

        /* MEET-09 참석자 추가 이벤트는 MEET-01 서비스 테스트에서 사용하지 않는다. */
        @Override
        public void publish(MeetingAttendeesAddedEvent event) {
            /* 호출되지 않는 별도 이벤트 계약이므로 기록하지 않는다. */
        }
    }
}
