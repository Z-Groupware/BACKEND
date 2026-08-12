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
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.query.GetDashboardMeetingsQuery;
import com.module06.backend.meeting.application.result.DashboardMeetingListResult;
import com.module06.backend.meeting.domain.model.DashboardMeetingScope;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository.DashboardMeetingCandidate;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository.DashboardMeetingCriteria;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;

/*
 * MEET-17 서비스의 스코프별 배치 조립·기본값·역할 검증·오류 계약을 검증한다.
 */
@DisplayName("MEET-17 대시보드 최근 회의 조회 서비스")
class DashboardMeetingQueryServiceTest {

    /* 저장소가 반환한 회의에 회의실·프로젝트·참석자 수 표시값을 조립하는지 검증한다. */
    @Test
    @DisplayName("스코프 결과에 회의실·프로젝트 태그·참석자 수를 배치 조립하고 라벨은 null로 둔다")
    void assemblesDashboardMeetingsForRequestedScope() {
        RecordingDashboardMeetingRepository repository = new RecordingDashboardMeetingRepository(List.of(
                new DashboardMeetingCandidate(91L, "실시간 알림 아키텍처 논의", 12L, MeetingStatus.SCHEDULED, 2L,
                        LocalDateTime.of(2026, 8, 11, 10, 0)),
                new DashboardMeetingCandidate(95L, "9월 스프린트 리뷰", 13L, MeetingStatus.SCHEDULED, 4L,
                        LocalDateTime.of(2026, 8, 12, 10, 0))
        ));
        RecordingMeetingQueryRepository meetingQueryRepository = new RecordingMeetingQueryRepository(List.of(
                new MeetingQueryRepository.MeetingAttendeeReference(91L, 3L),
                new MeetingQueryRepository.MeetingAttendeeReference(91L, 7L),
                new MeetingQueryRepository.MeetingAttendeeReference(95L, 3L),
                new MeetingQueryRepository.MeetingAttendeeReference(95L, 9L)
        ));
        DashboardMeetingQueryService service = new DashboardMeetingQueryService(
                repository, meetingQueryRepository, meetingRoomPort(), projectPort()
        );

        /* limit을 생략한 사원(MEMBER)의 me 스코프 요청을 실행한다. */
        DashboardMeetingListResult result = service.getDashboardMeetings(new GetDashboardMeetingsQuery(
                10L, 3L, null, "MEMBER", DashboardMeetingScope.ME, null
        ));

        /* 기본 limit 5가 저장소 조건에 그대로 전달돼야 한다. */
        assertThat(repository.criteria.companyId()).isEqualTo(10L);
        assertThat(repository.criteria.scope()).isEqualTo(DashboardMeetingScope.ME);
        assertThat(repository.criteria.requesterMemberId()).isEqualTo(3L);
        assertThat(repository.criteria.limit()).isEqualTo(5);

        /* 회의별 참석자 수와 회의실·프로젝트 표시값이 순서대로 조립돼야 한다. */
        assertThat(result.meetings())
                .extracting(DashboardMeetingListResult.MeetingItem::meetingId)
                .containsExactly(91L, 95L);
        assertThat(result.meetings())
                .extracting(DashboardMeetingListResult.MeetingItem::attendeeCount)
                .containsExactly(2, 2);
        assertThat(result.meetings().get(0).room()).isEqualTo("회의실 B");
        assertThat(result.meetings().get(0).projectTag()).isEqualTo("COLLAB");
        assertThat(result.meetings().get(1).room()).isEqualTo("회의실 A");
        assertThat(result.meetings().get(1).projectTag()).isEqualTo("GOODS");

        /* originLabel·hostLabel은 팀 라벨 연결 전까지 항상 null이어야 한다. */
        assertThat(result.meetings())
                .extracting(DashboardMeetingListResult.MeetingItem::originLabel)
                .containsOnlyNulls();
        assertThat(result.meetings())
                .extracting(DashboardMeetingListResult.MeetingItem::hostLabel)
                .containsOnlyNulls();

        /* 참석자 배치 조회는 현재 후보 회의 식별자로 정확히 한 번만 호출돼야 한다. */
        assertThat(meetingQueryRepository.callCount).isEqualTo(1);
        assertThat(meetingQueryRepository.meetingIds).containsExactly(91L, 95L);
    }

    /* 후보 회의가 없으면 참석자·회의실·프로젝트 Port를 호출하지 않고 빈 목록을 반환해야 한다. */
    @Test
    @DisplayName("스코프 결과가 비어 있으면 배치 Port를 호출하지 않고 빈 목록을 반환한다")
    void returnsEmptyResultWithoutPortCallsWhenNoCandidates() {
        DashboardMeetingQueryService service = new DashboardMeetingQueryService(
                new RecordingDashboardMeetingRepository(List.of()),
                neverCalledMeetingQueryRepository(),
                neverCalledMeetingRoomPort(),
                neverCalledProjectPort()
        );

        DashboardMeetingListResult result = service.getDashboardMeetings(new GetDashboardMeetingsQuery(
                10L, 3L, null, "MEMBER", DashboardMeetingScope.ME, 5
        ));

        assertThat(result.meetings()).isEmpty();
    }

    /* scope 누락은 저장소 접근 전에 Z-001 공통 입력 오류로 거절돼야 한다. */
    @Test
    @DisplayName("scope가 없으면 Z-001 공통 입력 오류로 거절한다")
    void rejectsMissingScope() {
        DashboardMeetingQueryService service = new DashboardMeetingQueryService(
                neverCalledDashboardMeetingRepository(),
                neverCalledMeetingQueryRepository(),
                neverCalledMeetingRoomPort(),
                neverCalledProjectPort()
        );

        assertThatThrownBy(() -> service.getDashboardMeetings(new GetDashboardMeetingsQuery(
                10L, 3L, null, "MEMBER", null, 5
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    /* limit이 허용 범위(1~20)를 벗어나면 Z-001 공통 입력 오류로 거절돼야 한다. */
    @Test
    @DisplayName("limit이 1~20 범위를 벗어나면 Z-001 공통 입력 오류로 거절한다")
    void rejectsLimitOutOfRange() {
        DashboardMeetingQueryService service = new DashboardMeetingQueryService(
                neverCalledDashboardMeetingRepository(),
                neverCalledMeetingQueryRepository(),
                neverCalledMeetingRoomPort(),
                neverCalledProjectPort()
        );

        assertThatThrownBy(() -> service.getDashboardMeetings(new GetDashboardMeetingsQuery(
                10L, 3L, null, "MEMBER", DashboardMeetingScope.ME, 21
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    /* MEMBER의 owner 스코프 요청은 역할 불일치로 Z-002 접근 거부여야 한다. */
    @Test
    @DisplayName("OWNER가 아닌 역할의 scope=owner 요청은 Z-002 접근 거부로 처리한다")
    void rejectsOwnerScopeForNonOwnerRole() {
        DashboardMeetingQueryService service = new DashboardMeetingQueryService(
                neverCalledDashboardMeetingRepository(),
                neverCalledMeetingQueryRepository(),
                neverCalledMeetingRoomPort(),
                neverCalledProjectPort()
        );

        assertThatThrownBy(() -> service.getDashboardMeetings(new GetDashboardMeetingsQuery(
                10L, 3L, null, "MEMBER", DashboardMeetingScope.OWNER, 5
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    /* MEMBER의 team 스코프 요청은 역할 불일치로 Z-002 접근 거부여야 한다. */
    @Test
    @DisplayName("LEADER가 아닌 역할의 scope=team 요청은 Z-002 접근 거부로 처리한다")
    void rejectsTeamScopeForNonLeaderRole() {
        DashboardMeetingQueryService service = new DashboardMeetingQueryService(
                neverCalledDashboardMeetingRepository(),
                neverCalledMeetingQueryRepository(),
                neverCalledMeetingRoomPort(),
                neverCalledProjectPort()
        );

        assertThatThrownBy(() -> service.getDashboardMeetings(new GetDashboardMeetingsQuery(
                10L, 3L, 100L, "MEMBER", DashboardMeetingScope.TEAM, 5
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    /* 테스트가 아는 회사 10의 회의실 표시 정보만 반환하는 Port다. */
    private MeetingRoomQueryPort meetingRoomPort() {
        return new MeetingRoomQueryPort() {
            @Override
            public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
                throw new AssertionError("MEET-17은 활성 단건 회의실 조회를 호출하면 안 됩니다.");
            }

            @Override
            public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
                return List.of(
                        new MeetingRoomSnapshot(2L, "회의실 B", "박애관 422호", null, null),
                        new MeetingRoomSnapshot(4L, "회의실 A", "박애관 401호", null, null)
                ).stream().filter(room -> meetingRoomIds.contains(room.meetingRoomId())).toList();
            }
        };
    }

    /* 호출되면 즉시 실패하는 회의실 Port 대역이다. */
    private MeetingRoomQueryPort neverCalledMeetingRoomPort() {
        return new MeetingRoomQueryPort() {
            @Override
            public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
                throw new AssertionError("호출되면 안 되는 회의실 Port입니다.");
            }

            @Override
            public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
                throw new AssertionError("호출되면 안 되는 회의실 Port입니다.");
            }
        };
    }

    /* 테스트가 아는 회사 10의 프로젝트 표시 정보만 반환하는 Port다. */
    private ProjectQueryPort projectPort() {
        return new ProjectQueryPort() {
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                throw new AssertionError("MEET-17은 활성 존재 확인을 호출하면 안 됩니다.");
            }

            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                return List.of(
                        new ProjectSnapshot(12L, "COLLAB", "실시간 협업", "#5B5BD6"),
                        new ProjectSnapshot(13L, "GOODS", "상품 리뉴얼", "#222222")
                ).stream().filter(project -> projectIds.contains(project.projectId())).toList();
            }
        };
    }

    /* 호출되면 즉시 실패하는 프로젝트 Port 대역이다. */
    private ProjectQueryPort neverCalledProjectPort() {
        return new ProjectQueryPort() {
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                throw new AssertionError("호출되면 안 되는 프로젝트 Port입니다.");
            }

            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                throw new AssertionError("호출되면 안 되는 프로젝트 Port입니다.");
            }
        };
    }

    /* 호출되면 즉시 실패하는 대시보드 저장소 대역이다. */
    private DashboardMeetingRepository neverCalledDashboardMeetingRepository() {
        return criteria -> {
            throw new AssertionError("호출되면 안 되는 대시보드 저장소입니다.");
        };
    }

    /* 호출되면 즉시 실패하는 회의 조회 저장소 대역이다. */
    private MeetingQueryRepository neverCalledMeetingQueryRepository() {
        return new RecordingMeetingQueryRepository(List.of()) {
            @Override
            public List<MeetingAttendeeReference> findMeetingAttendees(Long companyId, List<Long> meetingIds) {
                throw new AssertionError("호출되면 안 되는 참석자 배치 조회입니다.");
            }
        };
    }

    /* 서비스가 전달한 스코프 조건을 기록하고 준비된 후보 목록을 반환하는 저장소 대역이다. */
    private static final class RecordingDashboardMeetingRepository implements DashboardMeetingRepository {

        private final List<DashboardMeetingCandidate> candidates;
        private DashboardMeetingCriteria criteria;

        private RecordingDashboardMeetingRepository(List<DashboardMeetingCandidate> candidates) {
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public List<DashboardMeetingCandidate> findDashboardMeetings(DashboardMeetingCriteria criteria) {
            this.criteria = criteria;
            return candidates;
        }
    }

    /* 참석자 배치 조회 호출만 구현하고 나머지 계약은 호출되면 실패하는 저장소 대역이다. */
    private static class RecordingMeetingQueryRepository implements MeetingQueryRepository {

        private final List<MeetingAttendeeReference> attendees;
        private int callCount;
        private Long companyId;
        private List<Long> meetingIds;

        private RecordingMeetingQueryRepository(List<MeetingAttendeeReference> attendees) {
            this.attendees = List.copyOf(attendees);
        }

        @Override
        public List<MeetingAttendeeReference> findMeetingAttendees(Long companyId, List<Long> meetingIds) {
            this.callCount++;
            this.companyId = companyId;
            this.meetingIds = meetingIds;
            return attendees.stream().filter(reference -> meetingIds.contains(reference.meetingId())).toList();
        }

        @Override
        public Optional<MeetingSnapshot> findMeeting(Long companyId, Long meetingId) {
            throw new AssertionError("MEET-17은 단건 회의 조회를 호출하면 안 됩니다.");
        }

        @Override
        public List<ProjectMeetingSnapshot> findProjectMeetingsOrdered(Long companyId, Long projectId) {
            throw new AssertionError("MEET-17은 프로젝트 타임라인 조회를 호출하면 안 됩니다.");
        }

        @Override
        public Map<Long, Long> countMeetingsByProjectIds(Long companyId, List<Long> projectIds) {
            throw new AssertionError("MEET-17은 프로젝트별 회의 수 집계를 호출하면 안 됩니다.");
        }

        @Override
        public List<UpcomingMeetingSnapshot> findUpcomingMeetings(
                Long companyId, Long memberId, LocalDateTime now, int limit
        ) {
            throw new AssertionError("MEET-17은 예정 회의 조회를 호출하면 안 됩니다.");
        }

        @Override
        public List<MeetingTopicSnapshot> findMeetingTopics(Long companyId, List<Long> meetingIds) {
            throw new AssertionError("MEET-17은 안건 조회를 호출하면 안 됩니다.");
        }
    }
}
