package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.query.GetMeetingListQuery;
import com.module06.backend.meeting.application.result.MeetingListResult;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingListRepository;

/*
 * MEET-02 서비스의 기본 기간·페이지·외부 표시값 조합과 오류 계약을 검증한다.
 */
@DisplayName("MEET-02 회의 목록 조회 서비스")
class MeetingListQueryServiceTest {

    /* 현재 날짜를 2026년 8월 7일 KST로 고정하는 테스트 시계다. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-07T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* 기본 기간과 페이지가 적용되고 회의실·프로젝트·액션 수가 배치 조립되는지 검증한다. */
    @Test
    @DisplayName("기본 최근 3개월 회의에 회의실과 프로젝트 표시값을 조립한다")
    void appliesDefaultsAndAssemblesMeetingList() {
        /* 회의 두 건과 전체 37건인 저장소 페이지를 준비한다. */
        RecordingMeetingListRepository repository = new RecordingMeetingListRepository(
                new MeetingListRepository.MeetingPage(
                        List.of(
                                meeting(92L, 13L, 4L, "최근 회의", 8, 6, 2),
                                meeting(91L, 12L, 2L, "이전 회의", 8, 4, 4)
                        ),
                        37L,
                        2
                )
        );
        RecordingActionQueryPort actionQueryPort = new RecordingActionQueryPort(List.of(
                new ActionQueryPort.MeetingActionCount(92L, 3L),
                new ActionQueryPort.MeetingActionCount(999L, 7L)
        ));
        MeetingListQueryService service = new MeetingListQueryService(
                repository,
                meetingRoomPort(),
                projectPort(),
                actionQueryPort,
                FIXED_CLOCK
        );

        /* 기간과 페이지를 모두 생략한 OWNER 회사 전체 조회를 실행한다. */
        MeetingListResult result = service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, null, null, null, null, null, null
        ));

        /* 생략 기간은 2026-05-07 00시부터 2026-08-07 마지막 순간까지여야 한다. */
        assertThat(repository.criteria.fromInclusive()).isEqualTo(LocalDateTime.of(2026, 5, 7, 0, 0));
        assertThat(repository.criteria.toInclusive().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 7));

        /* 생략 페이지 값은 0번 페이지와 20건으로 저장소에 전달돼야 한다. */
        assertThat(repository.criteria.page()).isZero();
        assertThat(repository.criteria.size()).isEqualTo(20);
        assertThat(repository.criteria.companyWideRead()).isTrue();

        /* 저장소 순서와 참석자 수 및 전체 페이지 메타가 손실 없이 반환돼야 한다. */
        assertThat(result.meetings())
                .extracting(MeetingListResult.MeetingItem::meetingId)
                .containsExactly(92L, 91L);
        assertThat(result.meetings())
                .extracting(MeetingListResult.MeetingItem::attendeeCount)
                .containsExactly(2, 4);
        assertThat(result.meetings())
                .extracting(MeetingListResult.MeetingItem::actionCount)
                .containsExactly(3L, 0L);
        assertThat(result.page().totalElements()).isEqualTo(37L);
        assertThat(result.page().totalPages()).isEqualTo(2);

        /* 회의 식별자에 맞는 회의실 이름과 프로젝트 태그가 배치 결과에서 조립돼야 한다. */
        assertThat(result.meetings().get(0).meetingRoom().name()).isEqualTo("회의실 D");
        assertThat(result.meetings().get(1).project().tag()).isEqualTo("acommerce");

        /* 액션 도메인은 현재 페이지 회의 ID만 회사 범위와 함께 정확히 한 번 호출해야 한다. */
        assertThat(actionQueryPort.companyId).isEqualTo(10L);
        assertThat(actionQueryPort.meetingIds).containsExactly(92L, 91L);
        assertThat(actionQueryPort.callCount).isEqualTo(1);
    }

    /* 일반 구성원의 제한 열람 조건과 선택 필터가 저장소까지 유지되는지 검증한다. */
    @Test
    @DisplayName("MEMBER의 프로젝트·회의실·기간·상태 필터를 제한 범위로 전달한다")
    void passesRestrictedScopeAndFilters() {
        /* 빈 페이지를 반환하되 전달 조건을 기록하는 저장소를 준비한다. */
        RecordingMeetingListRepository repository = new RecordingMeetingListRepository(
                new MeetingListRepository.MeetingPage(List.of(), 0L, 0)
        );
        RecordingActionQueryPort actionQueryPort = new RecordingActionQueryPort(List.of());
        MeetingListQueryService service = new MeetingListQueryService(
                repository,
                meetingRoomPort(),
                projectPort(),
                actionQueryPort,
                FIXED_CLOCK
        );

        /* 모든 선택 필터와 2번 페이지 50건을 지정한 일반 구성원 조회를 실행한다. */
        service.getMeetings(new GetMeetingListQuery(
                10L,
                7L,
                false,
                12L,
                2L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 5),
                MeetingStatus.DONE,
                2,
                50
        ));

        /* 일반 사용자의 식별자와 제한 열람 플래그가 저장소 권한 조건으로 전달돼야 한다. */
        assertThat(repository.criteria.requesterMemberId()).isEqualTo(7L);
        assertThat(repository.criteria.companyWideRead()).isFalse();

        /* 요청 필터와 페이지가 변형 없이 저장소 조건에 포함돼야 한다. */
        assertThat(repository.criteria.projectId()).isEqualTo(12L);
        assertThat(repository.criteria.meetingRoomId()).isEqualTo(2L);
        assertThat(repository.criteria.status()).isEqualTo(MeetingStatus.DONE);
        assertThat(repository.criteria.page()).isEqualTo(2);
        assertThat(repository.criteria.size()).isEqualTo(50);

        /* 빈 페이지에서는 불필요한 액션 배치 조회를 실행하지 않아야 한다. */
        assertThat(actionQueryPort.callCount).isZero();
    }

    /* 잘못된 날짜 범위와 페이지 값이 저장소 전에 Z-001로 거절되는지 검증한다. */
    @Test
    @DisplayName("잘못된 기간과 페이지 범위는 Z-001로 거절한다")
    void rejectsInvalidRangeAndPage() {
        /* 잘못된 요청에서는 호출되지 않아야 하는 빈 저장소와 정상 표시 Port로 서비스를 만든다. */
        MeetingListQueryService service = new MeetingListQueryService(
                new RecordingMeetingListRepository(new MeetingListRepository.MeetingPage(List.of(), 0L, 0)),
                meetingRoomPort(),
                projectPort(),
                new RecordingActionQueryPort(List.of()),
                FIXED_CLOCK
        );

        /* 시작일이 종료일보다 늦은 요청은 공통 입력 오류여야 한다. */
        assertErrorCode(() -> service.getMeetings(new GetMeetingListQuery(
                10L,
                3L,
                true,
                null,
                null,
                LocalDate.of(2026, 8, 6),
                LocalDate.of(2026, 8, 5),
                null,
                0,
                20
        )), "Z-001");

        /* 음수 페이지와 최대 100을 초과한 크기도 같은 입력 오류여야 한다. */
        assertErrorCode(() -> service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, null, null, null, null, -1, 20
        )), "Z-001");
        assertErrorCode(() -> service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, null, null, null, null, 0, 101
        )), "Z-001");
    }

    /* 다른 회사 또는 존재하지 않는 프로젝트·회의실 필터의 도메인 오류를 검증한다. */
    @Test
    @DisplayName("회사 범위에 없는 프로젝트와 회의실은 PJ-001과 MR-001로 거절한다")
    void rejectsUnknownFilterReferences() {
        /* 어떤 표시값도 반환하지 않는 Port로 필터 소속 실패 상황을 만든다. */
        MeetingListQueryService service = new MeetingListQueryService(
                new RecordingMeetingListRepository(new MeetingListRepository.MeetingPage(List.of(), 0L, 0)),
                emptyMeetingRoomPort(),
                emptyProjectPort(),
                new RecordingActionQueryPort(List.of()),
                FIXED_CLOCK
        );

        /* 회사 범위에 없는 프로젝트 필터는 프로젝트 표준 not-found 오류여야 한다. */
        assertErrorCode(() -> service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, 999L, null, null, null, null, 0, 20
        )), "PJ-001");

        /* 프로젝트를 생략하고 없는 회의실을 지정하면 회의실 표준 not-found 오류여야 한다. */
        assertErrorCode(() -> service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, 999L, null, null, null, 0, 20
        )), "MR-001");
    }

    /* 두 회의실 표시 정보를 요청 식별자와 무관한 순서로 반환하는 Port 대역이다. */
    private MeetingRoomQueryPort meetingRoomPort() {
        /* 단건 활성 조회는 사용하지 않고 회사 범위 배치 표시 조회만 구현한다. */
        return new MeetingRoomQueryPort() {
            /* MEET-02는 활성 단건 계약을 호출하지 않는다. */
            @Override
            public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
                /* 호출되지 않는 기존 계약을 빈 결과로 만족시킨다. */
                return Optional.empty();
            }

            /* 요청된 회의실 중 테스트가 아는 2번과 4번의 표시 정보를 반환한다. */
            @Override
            public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
                /* 필터 검증과 페이지 표시 조합 모두에 사용할 회사 10의 회의실 목록이다. */
                return List.of(
                        new MeetingRoomSnapshot(4L, "회의실 D", null, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new MeetingRoomSnapshot(2L, "회의실 B", null, LocalTime.of(9, 0), LocalTime.of(18, 0))
                ).stream().filter(room -> meetingRoomIds.contains(room.meetingRoomId())).toList();
            }
        };
    }

    /* 회의실 필터 검증에서 빈 회사 범위 결과를 반환하는 Port 대역이다. */
    private MeetingRoomQueryPort emptyMeetingRoomPort() {
        /* 단건과 배치 모두 빈 결과를 반환해 MR-001 경로를 만든다. */
        return new MeetingRoomQueryPort() {
            /* 활성 회의실 단건은 존재하지 않는다. */
            @Override
            public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
                /* 요청 회사에 해당 회의실이 없음을 표현한다. */
                return Optional.empty();
            }

            /* 회사 범위 회의실 배치 결과도 비어 있다. */
            @Override
            public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
                /* 요청 식별자의 존재 여부를 숨긴 빈 목록을 반환한다. */
                return List.of();
            }
        };
    }

    /* 두 프로젝트 표시 정보를 요청 식별자와 무관한 순서로 반환하는 Port 대역이다. */
    private ProjectQueryPort projectPort() {
        /* 활성 존재 확인과 soft-delete 포함 표시 배치 조회를 함께 구현한다. */
        return new ProjectQueryPort() {
            /* 이 서비스에서는 기존 활성 단건 계약을 사용하지 않는다. */
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                /* 테스트 프로젝트는 모두 정상이라고 가정한다. */
                return true;
            }

            /* 요청된 프로젝트 중 테스트가 아는 12번과 13번 표시 정보를 반환한다. */
            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                /* 필터 검증과 페이지 표시 조합 모두에 사용할 회사 10의 프로젝트 목록이다. */
                return List.of(
                        new ProjectSnapshot(13L, "platform", "플랫폼 개편", "#222222"),
                        new ProjectSnapshot(12L, "acommerce", "A커머스 온보딩", "#5B5BD6")
                ).stream().filter(project -> projectIds.contains(project.projectId())).toList();
            }
        };
    }

    /* 프로젝트 필터 검증에서 빈 회사 범위 결과를 반환하는 Port 대역이다. */
    private ProjectQueryPort emptyProjectPort() {
        /* 활성 확인과 배치 표시 조회 모두 실패하는 프로젝트 대역이다. */
        return new ProjectQueryPort() {
            /* 어떤 프로젝트도 활성으로 인정하지 않는다. */
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                /* 테스트의 없는 프로젝트 상태를 반환한다. */
                return false;
            }

            /* 회사 범위 프로젝트 표시 정보가 없음을 빈 목록으로 반환한다. */
            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                /* 요청 프로젝트의 존재 여부를 숨긴 빈 결과다. */
                return List.of();
            }
        };
    }

    /* 테스트 조건으로 MEET-02 저장소 읽기 모델 한 건을 만든다. */
    private MeetingListRepository.MeetingListSnapshot meeting(
            Long meetingId,
            Long projectId,
            Long meetingRoomId,
            String title,
            int month,
            int day,
            int attendeeCount
    ) {
        /* 오후 2시부터 한 시간 진행되는 완료 회의 목록 모델을 반환한다. */
        LocalDateTime startAt = LocalDateTime.of(2026, month, day, 14, 0);
        return new MeetingListRepository.MeetingListSnapshot(
                meetingId,
                projectId,
                meetingRoomId,
                title,
                MeetingStatus.DONE,
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

    /* 서비스가 전달한 조건을 기록하고 준비된 회의 페이지를 반환하는 저장소 대역이다. */
    private static final class RecordingMeetingListRepository implements MeetingListRepository {

        /* 서비스 호출에 반환할 고정 회의 페이지다. */
        private final MeetingPage meetingPage;

        /* 서비스가 검증과 기본값 처리 후 전달한 실제 저장소 조건이다. */
        private MeetingListCriteria criteria;

        /* 테스트가 지정한 페이지 결과로 저장소 대역을 생성한다. */
        private RecordingMeetingListRepository(MeetingPage meetingPage) {
            /* 변경 불가능한 record 결과를 그대로 보관한다. */
            this.meetingPage = meetingPage;
        }

        /* 조회 조건을 기록하고 준비된 회의 페이지를 반환한다. */
        @Override
        public MeetingPage findMeetings(MeetingListCriteria criteria) {
            /* 서비스가 만든 실제 조건을 테스트 검증을 위해 보관한다. */
            this.criteria = criteria;
            return meetingPage;
        }
    }

    /* MEET-02가 액션 수를 현재 페이지 단위로 한 번만 요청하는지 기록하는 Port 대역이다. */
    private static final class RecordingActionQueryPort implements ActionQueryPort {

        /* 서비스 호출에 반환할 회의별 전체 액션 수다. */
        private final List<MeetingActionCount> actionCounts;

        /* 실제 배치 호출 횟수와 회사·회의 식별자를 검증하기 위한 기록이다. */
        private int callCount;
        private Long companyId;
        private List<Long> meetingIds;

        /* 테스트가 지정한 액션 집계 결과로 Port 대역을 생성한다. */
        private RecordingActionQueryPort(List<MeetingActionCount> actionCounts) {
            /* 변경 불가능한 목록으로 복사해 테스트 도중 반환값 변형을 막는다. */
            this.actionCounts = List.copyOf(actionCounts);
        }

        /* MEET-01 존재 검증 계약은 MEET-02 테스트에서 사용하지 않는다. */
        @Override
        public boolean existsAction(Long companyId, Long actionId) {
            /* 잘못된 호출이 생기면 테스트가 즉시 실패하도록 한다. */
            throw new AssertionError("MEET-02는 액션 단건 존재 검증을 호출하면 안 됩니다.");
        }

        /* MEET-10 분배 대기 계약은 MEET-02 테스트에서 사용하지 않는다. */
        @Override
        public List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                Long companyId,
                List<Long> meetingIds
        ) {
            /* 잘못된 호출이 생기면 테스트가 즉시 실패하도록 한다. */
            throw new AssertionError("MEET-02는 분배 대기 액션 조회를 호출하면 안 됩니다.");
        }

        /* 현재 페이지의 배치 호출 조건을 기록하고 준비된 액션 수를 반환한다. */
        @Override
        public List<MeetingActionCount> countActionsByMeetings(Long companyId, List<Long> meetingIds) {
            /* 호출 횟수와 전달값을 보관해 N+1 방지 계약을 검증한다. */
            this.callCount++;
            this.companyId = companyId;
            this.meetingIds = List.copyOf(meetingIds);
            return actionCounts;
        }
    }
}
