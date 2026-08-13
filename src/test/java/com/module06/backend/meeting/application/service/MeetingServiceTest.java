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
import com.module06.backend.meeting.application.event.MeetingAttendeesRemovedEvent;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.ActionQueryPort.ActionKind;
import com.module06.backend.meeting.application.port.out.ActionQueryPort.ActionTeamReference;
import com.module06.backend.meeting.application.port.out.MeetingEventPublisher;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort.MeetingRoomSnapshot;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort.ProjectSnapshot;
import com.module06.backend.meeting.application.result.MeetingCreationResult;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingAgenda;
import com.module06.backend.meeting.domain.repository.MeetingRepository;
import com.module06.backend.meeting.domain.repository.MeetingTopicRepository;

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
        RecordingMeetingTopicRepository topicRepository = new RecordingMeetingTopicRepository();
        RecordingMeetingEventPublisher eventPublisher = new RecordingMeetingEventPublisher();
        MeetingService service = service(
                repository,
                topicRepository,
                eventPublisher,
                activeRoom(),
                validMembers(),
                true,
                true
        );

        /* 개설자를 목록에서 생략한 정상 예약 요청을 실행한다. */
        MeetingCreationResult result = service.createMeeting(validCommand());

        /* 저장된 회의는 생성 ID와 SCHEDULED 상태를 가져야 한다. */
        assertThat(result.meetingId()).isEqualTo(91L);
        assertThat(result.status().name()).isEqualTo("SCHEDULED");
        assertThat(repository.savedMeeting).isNotNull();
        assertThat(repository.savedMeeting.getTeamId()).isEqualTo(100L);

        /* 저장된 회의 식별자 아래에 정규화된 MAIN·SUB 안건이 함께 전달돼야 한다. */
        assertThat(topicRepository.savedMeetingId).isEqualTo(91L);
        assertThat(topicRepository.savedAgenda.mainTopic()).isEqualTo("스프린트 진행 상황");
        assertThat(topicRepository.savedAgenda.subTopics()).containsExactly("개발 진행률 점검");

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

    /* null 참석자 식별자가 정규화 과정의 500 오류로 번지지 않는지 검증한다. */
    @Test
    @DisplayName("참석자 목록에 null 식별자가 있으면 MT-010으로 거절한다")
    void rejectsNullAttendeeIdentifier() {
        /* Bean Validation을 거치지 않는 서비스 직접 호출 상황의 null 원소를 준비한다. */
        CreateMeetingCommand command = command(
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                java.util.Arrays.asList(7L, null)
        );

        /* List.copyOf의 NullPointerException 대신 참석자 계약 오류가 반환돼야 한다. */
        assertErrorCode(() -> defaultService().createMeeting(command), "MT-010");
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

    /* OWNER가 액션을 선택하지 않은 회의가 ActionQueryPort 조회 없이 진행 가능한지 검증한다. */
    @Test
    @DisplayName("OWNER가 관련 액션을 선택하지 않으면 액션 조회 없이 회의를 예약한다")
    void createsMeetingWithoutRelatedAction() {
        /* 호출되는 순간 실패하는 액션 Port를 포함해 나머지 정상 의존성을 직접 조립한다. */
        ActionQueryPort pendingActionPort = new ActionQueryPort() {
            /* OWNER의 액션 없는 경로에서는 팀 조회도 호출되면 안 된다. */
            @Override
            public Optional<ActionTeamReference> findActionTeamReference(Long companyId, Long actionId) {
                throw new AssertionError("relatedActionId가 없으면 액션 팀 조회를 호출하면 안 됩니다.");
            }

            /* MEET-10 배치 조회는 회의 예약 경로에서 사용하지 않는다. */
            @Override
            public java.util.List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                    Long companyId,
                    java.util.List<Long> meetingIds
            ) {
                throw new AssertionError("회의 예약 경로에서 분배 대기 배치 조회를 호출하면 안 됩니다.");
            }

            /* MEET-02 액션 수 조회도 회의 예약 경로에서 사용하지 않는다. */
            @Override
            public java.util.List<MeetingActionCount> countActionsByMeetings(
                    Long companyId,
                    java.util.List<Long> meetingIds
            ) {
                throw new AssertionError("회의 예약 경로에서 액션 수 배치 조회를 호출하면 안 됩니다.");
            }
        };
        MeetingService service = serviceWithActionPort(pendingActionPort);

        /* 선택 입력인 relatedActionId만 null인 정상 회의 예약 명령을 준비한다. */
        CreateMeetingCommand command = new CreateMeetingCommand(
                10L,
                3L,
                100L,
                "OWNER",
                "A커머스 온보딩 킥오프",
                12L,
                2L,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                true,
                null,
                List.of(7L, 11L),
                "스프린트 진행 상황",
                List.of("개발 진행률 점검")
        );

        /* 액션 Port를 호출하지 않고 예약이 완료되며 관련 액션은 null로 유지돼야 한다. */
        MeetingCreationResult result = service.createMeeting(command);
        assertThat(result.meetingId()).isEqualTo(91L);
    }

    /* OWNER가 상위 팀 액션을 보내면 역할 정책 오류로 거절되는지 검증한다. */
    @Test
    @DisplayName("OWNER가 관련 액션을 지정하면 MT-016으로 거절한다")
    void rejectsRelatedActionFromOwner() {
        /* 정상 명령에서 역할만 OWNER로 바꿔 금지된 액션 입력 조합을 만든다. */
        CreateMeetingCommand command = command("OWNER", 305L, List.of(7L), "대주제", List.of("소주제"));

        /* 외부 포트를 호출하기 전에 역할 정책 오류가 반환돼야 한다. */
        assertErrorCode(() -> defaultService().createMeeting(command), "MT-016");
    }

    /* OWNER 외 역할이 상위 팀 액션을 생략하면 거절되는지 검증한다. */
    @Test
    @DisplayName("OWNER 외 역할이 관련 액션을 생략하면 MT-016으로 거절한다")
    void rejectsMissingRelatedActionFromNonOwner() {
        /* LEADER가 필수 상위 팀 액션을 보내지 않은 명령을 만든다. */
        CreateMeetingCommand command = command("LEADER", null, List.of(7L), "대주제", List.of("소주제"));

        /* 액션 존재 조회 전에 역할 정책 오류가 반환돼야 한다. */
        assertErrorCode(() -> defaultService().createMeeting(command), "MT-016");
    }

    /* host만 참석자 목록에 들어온 요청이 거절되는지 검증한다. */
    @Test
    @DisplayName("host 외 참석자가 없으면 MT-017로 거절한다")
    void rejectsMeetingWithoutInvitedAttendee() {
        /* 자동 포함될 host만 중복 전달한 명령을 만든다. */
        CreateMeetingCommand command = command("LEADER", 305L, List.of(3L), "대주제", List.of("소주제"));

        /* 구성원 조회 전에 최소 참석자 오류가 반환돼야 한다. */
        assertErrorCode(() -> defaultService().createMeeting(command), "MT-017");
    }

    /* 대주제 또는 소주제가 빠진 요청이 거절되는지 검증한다. */
    @Test
    @DisplayName("필수 안건이 없으면 MT-015로 거절한다")
    void rejectsMissingAgenda() {
        /* 대주제는 있지만 소주제 목록이 빈 명령을 만든다. */
        CreateMeetingCommand command = command("LEADER", 305L, List.of(7L), "대주제", List.of());

        /* 회의실과 프로젝트를 조회하기 전에 안건 오류가 반환돼야 한다. */
        assertErrorCode(() -> defaultService().createMeeting(command), "MT-015");
    }

    /* PERSONAL 액션을 상위 팀 액션으로 지정할 수 없는지 검증한다. */
    @Test
    @DisplayName("PERSONAL 액션을 연결하면 MT-018로 거절한다")
    void rejectsPersonalRelatedAction() {
        /* 액션은 존재하지만 종류가 PERSONAL인 Port 대역을 준비한다. */
        ActionQueryPort actionPort = actionPort(Optional.of(
                new ActionTeamReference(null, ActionKind.PERSONAL)
        ));

        /* 개인 액션은 회의의 상위 팀 액션이 될 수 없어야 한다. */
        assertErrorCode(() -> serviceWithActionPort(actionPort).createMeeting(validCommand()), "MT-018");
    }

    /* 다른 팀의 TEAM 액션을 연결할 수 없는지 검증한다. */
    @Test
    @DisplayName("다른 팀의 TEAM 액션을 연결하면 MT-019로 거절한다")
    void rejectsRelatedActionFromAnotherTeam() {
        /* host 팀 100과 다른 팀 200의 TEAM 액션을 반환하는 Port 대역을 준비한다. */
        ActionQueryPort actionPort = actionPort(Optional.of(
                new ActionTeamReference(200L, ActionKind.TEAM)
        ));

        /* 팀 경계가 다른 액션은 회의에 연결할 수 없어야 한다. */
        assertErrorCode(() -> serviceWithActionPort(actionPort).createMeeting(validCommand()), "MT-019");
    }

    /* 인증 정보의 팀과 실제 구성원 팀이 다를 때 요청 팀으로 검증을 우회할 수 없는지 확인한다. */
    @Test
    @DisplayName("인증 팀과 실제 개설자 팀이 다르면 실제 팀을 기준으로 액션을 검증한다")
    void validatesRelatedActionAgainstActualHostTeam() {
        /* 인증 정보와 액션은 팀 200으로 맞지만 B 도메인의 실제 개설자 팀은 100인 상황을 준비한다. */
        ActionQueryPort actionPort = actionPort(Optional.of(
                new ActionTeamReference(200L, ActionKind.TEAM)
        ));
        CreateMeetingCommand command = commandWithHostTeamId(200L);

        /* 조작되거나 오래된 인증 팀이 아니라 실제 구성원 팀 100을 기준으로 거절해야 한다. */
        assertErrorCode(() -> serviceWithActionPort(actionPort).createMeeting(command), "MT-019");
    }

    /* 같은 팀의 TEAM 액션 읽기 결과를 바꿔 검증 테스트에 사용하는 Port 대역을 만든다. */
    private ActionQueryPort actionPort(Optional<ActionTeamReference> reference) {
        /* MEET-01 단건 조회 외 계약은 호출 여부를 명시적으로 확인한다. */
        return new ActionQueryPort() {
            /* 테스트가 준비한 액션 팀 읽기 결과를 반환한다. */
            @Override
            public Optional<ActionTeamReference> findActionTeamReference(Long companyId, Long actionId) {
                return reference;
            }

            /* MEET-10 배치 계약은 회의 개설에서 사용하지 않는다. */
            @Override
            public List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                    Long companyId,
                    List<Long> meetingIds
            ) {
                throw new AssertionError("회의 개설에서 분배 대기 배치 조회를 호출하면 안 됩니다.");
            }

            /* MEET-02 액션 수 계약은 회의 개설에서 사용하지 않는다. */
            @Override
            public List<MeetingActionCount> countActionsByMeetings(Long companyId, List<Long> meetingIds) {
                throw new AssertionError("회의 개설에서 액션 수 배치 조회를 호출하면 안 됩니다.");
            }
        };
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
                new RecordingMeetingTopicRepository(),
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
        /* 안건 기록 여부를 따로 확인하지 않는 테스트에는 기본 기록 대역을 사용한다. */
        return service(
                repository,
                new RecordingMeetingTopicRepository(),
                eventPublisher,
                room,
                members,
                projectExists,
                actionExists
        );
    }

    /* 테스트 조건과 안건 저장 대역까지 전달받아 모든 의존성을 조립한다. */
    private MeetingService service(
            MeetingRepository repository,
            MeetingTopicRepository topicRepository,
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
            /* 정상값은 host 팀과 같은 TEAM 액션이며 미존재 조건에서는 빈 결과를 반환한다. */
            @Override
            public Optional<ActionTeamReference> findActionTeamReference(Long companyId, Long actionId) {
                return actionExists
                        ? Optional.of(new ActionTeamReference(100L, ActionKind.TEAM))
                        : Optional.empty();
            }

            /* MEET-10 배치 조회는 회의 예약 경로에서 사용하지 않는다. */
            @Override
            public java.util.List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                    Long companyId,
                    java.util.List<Long> meetingIds
            ) {
                return java.util.List.of();
            }

            /* MEET-02 액션 수 조회는 회의 예약 경로에서 사용하지 않는다. */
            @Override
            public java.util.List<MeetingActionCount> countActionsByMeetings(
                    Long companyId,
                    java.util.List<Long> meetingIds
            ) {
                return java.util.List.of();
            }
        };

        /* 고정 시계를 포함한 실제 서비스를 반환한다. */
        return new MeetingService(
                repository,
                topicRepository,
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

    /* 인증 principal의 팀 식별자만 바꾼 정상 형식 명령을 만든다. */
    private CreateMeetingCommand commandWithHostTeamId(Long hostTeamId) {
        /* B 도메인의 실제 팀과 다른 인증 팀을 전달하는 회귀 상황을 재현한다. */
        CreateMeetingCommand command = validCommand();
        return new CreateMeetingCommand(
                command.companyId(),
                command.hostMemberId(),
                hostTeamId,
                command.hostRole(),
                command.title(),
                command.projectId(),
                command.meetingRoomId(),
                command.startAt(),
                command.endAt(),
                command.recordingConsent(),
                command.relatedActionId(),
                command.attendeeMemberIds(),
                command.mainTopic(),
                command.subTopics()
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
                "LEADER",
                "A커머스 온보딩 킥오프",
                12L,
                2L,
                startAt,
                endAt,
                true,
                305L,
                attendeeMemberIds,
                "스프린트 진행 상황",
                List.of("개발 진행률 점검")
        );
    }

    /* 역할·액션·참석자·안건만 바꾼 회의 예약 명령을 만든다. */
    private CreateMeetingCommand command(
            String hostRole,
            Long relatedActionId,
            List<Long> attendeeMemberIds,
            String mainTopic,
            List<String> subTopics
    ) {
        /* 시간과 나머지 식별자는 정상값으로 고정해 해당 정책만 검증한다. */
        return new CreateMeetingCommand(
                10L,
                3L,
                100L,
                hostRole,
                "A커머스 온보딩 킥오프",
                12L,
                2L,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                true,
                relatedActionId,
                attendeeMemberIds,
                mainTopic,
                subTopics
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

    /* 서비스가 안건 저장 포트에 전달한 회의 식별자와 안건을 기록하는 대역이다. */
    private static final class RecordingMeetingTopicRepository implements MeetingTopicRepository {

        /* 안건이 저장된 회의 식별자다. */
        private Long savedMeetingId;

        /* 저장을 요청받은 대주제와 소주제 묶음이다. */
        private MeetingAgenda savedAgenda;

        /* 회의 식별자와 안건을 기록한다. */
        @Override
        public void saveAgenda(Long meetingId, MeetingAgenda agenda) {
            /* 테스트가 원자 저장 호출 여부와 내용을 확인할 수 있게 보존한다. */
            this.savedMeetingId = meetingId;
            this.savedAgenda = agenda;
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

        /* MEET-09 참석자 제외 이벤트도 MEET-01 서비스 테스트에서 사용하지 않는다. */
        @Override
        public void publish(MeetingAttendeesRemovedEvent event) {
            /* 호출되지 않는 별도 이벤트 계약이므로 기록하지 않는다. */
        }
    }
}
