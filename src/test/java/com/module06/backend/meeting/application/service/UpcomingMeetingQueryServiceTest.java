package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.query.GetUpcomingMeetingsQuery;
import com.module06.backend.meeting.application.result.UpcomingMeetingListResult;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;

/*
 * MEET-03 서비스의 기본 limit, 외부 표시값 배치 조립, 입장 가능 판정을 검증한다.
 */
@DisplayName("MEET-03 내 예정 회의 조회 서비스")
class UpcomingMeetingQueryServiceTest {

    /* 현재 시각을 2026-08-05 09:00 KST로 고정하는 테스트 시계다. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* 예정 회의 메타와 배치 표시값이 시간순 카드 결과로 조립되는지 검증한다. */
    @Test
    @DisplayName("예정 회의에 회의실·프로젝트·입장 가능 여부를 조립한다")
    void assemblesUpcomingMeetingCards() {
        /* 입장 가능한 09시 05분 회의와 아직 입장할 수 없는 10시 회의를 준비한다. */
        RecordingMeetingQueryRepository repository = new RecordingMeetingQueryRepository(List.of(
                meeting(91L, 12L, 2L, 3L, "킥오프", MeetingStatus.SCHEDULED, 9, 5, 4),
                meeting(92L, 13L, 4L, 7L, "진행 회의", MeetingStatus.IN_PROGRESS, 10, 0, 3)
        ));
        UpcomingMeetingQueryService service = new UpcomingMeetingQueryService(
                repository,
                meetingRoomPort(),
                projectPort(),
                FIXED_CLOCK
        );

        /* limit을 생략한 내부 Query로 로그인 사용자 3번의 예정 회의를 조회한다. */
        UpcomingMeetingListResult result = service.getUpcomingMeetings(
                new GetUpcomingMeetingsQuery(10L, 3L, null)
        );

        /* 생략된 limit은 기본값 5로 저장소에 전달돼야 한다. */
        assertThat(repository.capturedLimit).isEqualTo(5);

        /* 저장소 순서를 유지한 두 회의의 식별자와 참석자 수가 그대로 반환돼야 한다. */
        assertThat(result.meetings())
                .extracting(UpcomingMeetingListResult.MeetingItem::meetingId)
                .containsExactly(91L, 92L);
        assertThat(result.meetings())
                .extracting(UpcomingMeetingListResult.MeetingItem::attendeeCount)
                .containsExactly(4, 3);

        /* 요청자가 개설자인 첫 회의만 isHost가 true여야 한다. */
        assertThat(result.meetings())
                .extracting(UpcomingMeetingListResult.MeetingItem::host)
                .containsExactly(true, false);

        /* 09시 05분 회의는 입장 가능하고 10시 회의는 아직 입장할 수 없어야 한다. */
        assertThat(result.meetings())
                .extracting(UpcomingMeetingListResult.MeetingItem::entryAvailable)
                .containsExactly(true, false);

        /* 회의실 이름과 프로젝트 태그가 각 회의 식별자에 맞게 배치 결과에서 조립돼야 한다. */
        assertThat(result.meetings().get(0).meetingRoom().name()).isEqualTo("회의실 B");
        assertThat(result.meetings().get(0).project().tag()).isEqualTo("acommerce");
    }

    /* 빈 회의 결과가 외부 표시 정보 Port 호출 없이 정상 빈 목록이 되는지 검증한다. */
    @Test
    @DisplayName("예정 회의가 없으면 외부 Port를 호출하지 않고 빈 목록을 반환한다")
    void returnsEmptyListWithoutCallingDisplayPorts() {
        /* 호출되면 실패하는 Port와 빈 조회 저장소로 서비스를 구성한다. */
        MeetingRoomQueryPort roomPort = throwingMeetingRoomPort();
        ProjectQueryPort projectPort = throwingProjectPort();
        UpcomingMeetingQueryService service = new UpcomingMeetingQueryService(
                new RecordingMeetingQueryRepository(List.of()),
                roomPort,
                projectPort,
                FIXED_CLOCK
        );

        /* 정상 인증과 기본 limit으로 조회한 결과는 비어 있어야 한다. */
        UpcomingMeetingListResult result = service.getUpcomingMeetings(
                new GetUpcomingMeetingsQuery(10L, 3L, 5)
        );

        /* 목록 API 계약에 따라 null이 아닌 빈 배열 결과를 반환해야 한다. */
        assertThat(result.meetings()).isEmpty();
    }

    /* 명세 범위를 벗어난 limit이 저장소 호출 전에 Z-001로 거절되는지 검증한다. */
    @Test
    @DisplayName("limit이 1~20 범위를 벗어나면 Z-001로 거절한다")
    void rejectsOutOfRangeLimit() {
        /* 정상 외부 Port와 빈 저장소로 입력값 검증용 서비스를 준비한다. */
        UpcomingMeetingQueryService service = new UpcomingMeetingQueryService(
                new RecordingMeetingQueryRepository(List.of()),
                meetingRoomPort(),
                projectPort(),
                FIXED_CLOCK
        );

        /* 최솟값보다 작은 0과 최댓값보다 큰 21 모두 같은 공통 입력 오류여야 한다. */
        assertErrorCode(
                () -> service.getUpcomingMeetings(new GetUpcomingMeetingsQuery(10L, 3L, 0)),
                "Z-001"
        );
        assertErrorCode(
                () -> service.getUpcomingMeetings(new GetUpcomingMeetingsQuery(10L, 3L, 21)),
                "Z-001"
        );
    }

    /* 두 회의실 표시 정보를 배치로 반환하는 D 내부 Port 대역을 만든다. */
    private MeetingRoomQueryPort meetingRoomPort() {
        /* 단건 조회는 이 테스트에서 사용하지 않고 배치 조회만 실제 값을 반환한다. */
        return new MeetingRoomQueryPort() {
            /* MEET-01 단건 계약은 사용하지 않으므로 빈 결과를 반환한다. */
            @Override
            public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
                /* MEET-03 서비스는 이 메서드를 호출하지 않는다. */
                return Optional.empty();
            }

            /* 요청 회의실 두 건의 표시 정보를 순서와 무관하게 반환한다. */
            @Override
            public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
                /* 서비스가 식별자 맵으로 정확히 조립하는지 확인할 두 회의실을 반환한다. */
                return List.of(
                        new MeetingRoomSnapshot(4L, "회의실 D", null),
                        new MeetingRoomSnapshot(2L, "회의실 B", null)
                );
            }
        };
    }

    /* 어떤 조회 메서드가 호출돼도 실패해 빈 결과의 단축 경로를 검증하는 회의실 Port를 만든다. */
    private MeetingRoomQueryPort throwingMeetingRoomPort() {
        /* MEET-03 빈 결과에서는 단건·배치 회의실 조회가 모두 호출되지 않아야 한다. */
        return new MeetingRoomQueryPort() {
            /* 단건 회의실 조회가 호출되면 테스트를 즉시 실패시킨다. */
            @Override
            public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
                /* 빈 회의 결과에서는 표시 정보 조회가 불필요하다. */
                throw new AssertionError("빈 목록에서는 회의실 Port를 호출하면 안 됩니다.");
            }

            /* 배치 회의실 조회가 호출되면 테스트를 즉시 실패시킨다. */
            @Override
            public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
                /* 빈 회의 결과에서는 표시 정보 조회가 불필요하다. */
                throw new AssertionError("빈 목록에서는 회의실 Port를 호출하면 안 됩니다.");
            }
        };
    }

    /* 두 프로젝트 표시 정보를 배치로 반환하는 C 연동 Port 대역을 만든다. */
    private ProjectQueryPort projectPort() {
        /* MEET-01 존재 확인과 MEET-03 배치 표시 조회를 모두 구현한 테스트 대역을 반환한다. */
        return new ProjectQueryPort() {
            /* 이 테스트에서는 프로젝트 존재 확인 단건 계약을 사용하지 않는다. */
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                /* 준비된 프로젝트는 모두 정상이라고 가정한다. */
                return true;
            }

            /* 요청 프로젝트 두 건의 태그·이름·색상을 배치로 반환한다. */
            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                /* 서비스가 식별자 기준으로 조립할 수 있도록 역순 표시 정보를 반환한다. */
                return List.of(
                        new ProjectSnapshot(13L, "platform", "플랫폼 개편", "#222222"),
                        new ProjectSnapshot(12L, "acommerce", "A커머스 온보딩", "#5B5BD6")
                );
            }
        };
    }

    /* 어떤 조회 메서드가 호출돼도 실패해 빈 결과의 단축 경로를 검증하는 프로젝트 Port를 만든다. */
    private ProjectQueryPort throwingProjectPort() {
        /* MEET-03 빈 결과에서는 단건·배치 프로젝트 조회가 모두 호출되지 않아야 한다. */
        return new ProjectQueryPort() {
            /* 프로젝트 존재 확인이 호출되면 테스트를 즉시 실패시킨다. */
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                /* 빈 회의 결과에서는 프로젝트 조회가 불필요하다. */
                throw new AssertionError("빈 목록에서는 프로젝트 Port를 호출하면 안 됩니다.");
            }

            /* 프로젝트 표시 정보 조회가 호출되면 테스트를 즉시 실패시킨다. */
            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                /* 빈 회의 결과에서는 프로젝트 조회가 불필요하다. */
                throw new AssertionError("빈 목록에서는 프로젝트 Port를 호출하면 안 됩니다.");
            }
        };
    }

    /* 테스트 카드 조건으로 예정 회의 저장소 읽기 모델을 만든다. */
    private MeetingQueryRepository.UpcomingMeetingSnapshot meeting(
            Long meetingId,
            Long projectId,
            Long meetingRoomId,
            Long hostMemberId,
            String title,
            MeetingStatus status,
            int hour,
            int minute,
            int attendeeCount
    ) {
        /* 시작 한 시간 뒤 종료되는 정상 예정 회의 조회 모델을 반환한다. */
        LocalDateTime startAt = LocalDateTime.of(2026, 8, 5, hour, minute);
        return new MeetingQueryRepository.UpcomingMeetingSnapshot(
                meetingId,
                projectId,
                meetingRoomId,
                hostMemberId,
                title,
                status,
                startAt,
                startAt.plusHours(1),
                attendeeCount
        );
    }

    /* 실행 결과가 예상 서비스 오류 코드인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* 예외 타입과 외부 계약 코드를 함께 확인한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }

    /* MEET-03 서비스가 사용할 예정 회의 결과와 전달 limit을 기록하는 저장소 대역이다. */
    private static final class RecordingMeetingQueryRepository implements MeetingQueryRepository {

        /* 서비스에 반환할 예정 회의 읽기 모델 목록이다. */
        private final List<UpcomingMeetingSnapshot> meetings;

        /* 서비스가 저장소에 전달한 실제 조회 제한 개수다. */
        private int capturedLimit;

        /* 테스트가 지정한 예정 회의 목록으로 저장소 대역을 생성한다. */
        private RecordingMeetingQueryRepository(List<UpcomingMeetingSnapshot> meetings) {
            /* 외부 변경이 테스트 결과에 영향을 주지 않도록 목록을 복사한다. */
            this.meetings = List.copyOf(meetings);
        }

        /* 단건 회의 조회는 MEET-03 테스트에서 사용하지 않는다. */
        @Override
        public Optional<MeetingSnapshot> findMeeting(Long companyId, Long meetingId) {
            /* 호출되지 않는 기존 계약을 빈 결과로 만족시킨다. */
            return Optional.empty();
        }

        /* E 프로젝트 타임라인 조회는 MEET-03 테스트에서 사용하지 않는다. */
        @Override
        public List<ProjectMeetingSnapshot> findProjectMeetingsOrdered(Long companyId, Long projectId) {
            /* 호출되지 않는 기존 계약을 빈 목록으로 만족시킨다. */
            return List.of();
        }

        /* 프로젝트별 회의 수 조회는 MEET-03 테스트에서 사용하지 않는다. */
        @Override
        public Map<Long, Long> countMeetingsByProjectIds(Long companyId, List<Long> projectIds) {
            /* 호출되지 않는 신규 배치 계약을 빈 집계로 만족시킨다. */
            return Map.of();
        }

        /* MEET-03 조회 limit을 기록하고 준비된 예정 회의 목록을 반환한다. */
        @Override
        public List<UpcomingMeetingSnapshot> findUpcomingMeetings(
                Long companyId,
                Long memberId,
                LocalDateTime now,
                int limit
        ) {
            /* 서비스가 기본값 또는 요청값을 올바르게 전달했는지 확인할 수 있도록 기록한다. */
            this.capturedLimit = limit;
            return meetings;
        }

        /* E 회의 주제 조회는 MEET-03 테스트에서 사용하지 않는다. */
        @Override
        public List<MeetingTopicSnapshot> findMeetingTopics(Long companyId, List<Long> meetingIds) {
            /* 호출되지 않는 기존 계약을 빈 목록으로 만족시킨다. */
            return List.of();
        }

        /* E 회의 참석자 배치 조회는 MEET-03 테스트에서 사용하지 않는다. */
        @Override
        public List<MeetingAttendeeReference> findMeetingAttendees(Long companyId, List<Long> meetingIds) {
            /* 호출되지 않는 기존 계약을 빈 목록으로 만족시킨다. */
            return List.of();
        }
    }
}
