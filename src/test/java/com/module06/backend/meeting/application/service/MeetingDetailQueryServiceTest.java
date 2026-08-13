package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.port.out.SummaryStatusQueryPort;
import com.module06.backend.meeting.application.query.GetMeetingDetailQuery;
import com.module06.backend.meeting.application.result.MeetingDetailResult;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingSummaryStatus;
import com.module06.backend.meeting.domain.repository.MeetingDetailRepository;
import com.module06.backend.meeting.domain.repository.MeetingDetailRepository.MeetingDetailSnapshot;

/*
 * MEET-04 상세 조회 서비스의 테넌트·권한·외부 표시 정보 조합 규칙을 검증한다.
 */
@DisplayName("MEET-04 회의 상세 조회 서비스")
class MeetingDetailQueryServiceTest {

    /* 참석자가 회의 메타와 연결 리소스 및 개설자 우선 명단을 조회하는지 검증한다. */
    @Test
    @DisplayName("참석자는 회의 상세와 개설자 우선 명단을 조회한다")
    void attendeeReadsMeetingDetail() {
        /* 정상 회의와 프로젝트·회의실·구성원 표시 정보를 반환하는 서비스를 준비한다. */
        MeetingDetailQueryService service = service(Optional.of(meeting()));

        /* 참석자 7번이 회사 10의 91번 회의를 상세 조회한다. */
        MeetingDetailResult result = service.getMeetingDetail(
                new GetMeetingDetailQuery(10L, 7L, 200L, "MEMBER", false, 91L)
        );

        /* 회의 메타와 녹음 동의 및 연결 프로젝트·회의실 값이 보존돼야 한다. */
        assertThat(result.meetingId()).isEqualTo(91L);
        assertThat(result.status()).isEqualTo(MeetingStatus.DONE);
        assertThat(result.recordingConsent()).isTrue();
        assertThat(result.project().tag()).isEqualTo("acommerce");
        assertThat(result.meetingRoom().location()).isEqualTo("박애관 422호");

        /* 개설자 정보와 참석자 순서 및 B Port의 직급 표시값이 응답에 포함돼야 한다. */
        assertThat(result.host().memberId()).isEqualTo(3L);
        assertThat(result.attendees())
                .extracting(MeetingDetailResult.Attendee::memberId)
                .containsExactly(3L, 7L, 11L);
        assertThat(result.attendees())
                .extracting(MeetingDetailResult.Attendee::jobPosition)
                .containsExactly("팀장", "시니어", "디자이너");

        /* 미확정 액션이 없고 요약도 중단되지 않았으면 0건과 알 수 없음(null)이어야 한다. */
        assertThat(result.pendingActionCount()).isZero();
        assertThat(result.summaryStatus()).isNull();
    }

    /* 종료된 회의에서 C·A Port가 돌려준 값이 그대로 반영되는지 검증한다. */
    @Test
    @DisplayName("종료된 회의는 미확정 액션 건수와 STALLED 요약 상태를 조립한다")
    void includesPendingActionCountAndStalledSummaryStatusForDoneMeeting() {
        /* 미확정 액션 3건과 요약 중단을 보고하는 Port로 서비스를 준비한다. */
        MeetingDetailQueryService service = service(
                Optional.of(meeting()),
                pendingActionPort(91L, 3L),
                stalledSummaryPort(91L)
        );

        MeetingDetailResult result = service.getMeetingDetail(
                new GetMeetingDetailQuery(10L, 7L, 200L, "MEMBER", false, 91L)
        );

        /* C·A가 회의 ID 하나짜리 목록으로 돌려준 값이 그대로 반영돼야 한다. */
        assertThat(result.pendingActionCount()).isEqualTo(3L);
        assertThat(result.summaryStatus()).isEqualTo(MeetingSummaryStatus.STALLED);
    }

    /* 아직 종료되지 않은 회의는 C·A Port를 아예 부르지 않고 기본값으로 확정하는지 검증한다. */
    @Test
    @DisplayName("종료 전 회의는 액션·요약 Port를 호출하지 않고 0건·NONE으로 확정한다")
    void skipsActionAndSummaryPortsBeforeMeetingEnds() {
        /* 아직 IN_PROGRESS인 회의와, 호출되면 즉시 실패하는 Port로 서비스를 준비한다. */
        MeetingDetailQueryService service = service(
                Optional.of(meeting(MeetingStatus.IN_PROGRESS)),
                neverCalledActionPort(),
                neverCalledSummaryStatusPort()
        );

        MeetingDetailResult result = service.getMeetingDetail(
                new GetMeetingDetailQuery(10L, 7L, 200L, "MEMBER", false, 91L)
        );

        /* Port를 부르지 않고도 0건과 NONE이 확정돼야 한다. */
        assertThat(result.pendingActionCount()).isZero();
        assertThat(result.summaryStatus()).isEqualTo(MeetingSummaryStatus.NONE);
    }

    /* 같은 팀 LEADER가 직접 참석하지 않아도 팀 회의를 읽을 수 있는지 검증한다. */
    @Test
    @DisplayName("LEADER는 같은 팀에서 개설된 회의를 조회한다")
    void teamLeaderReadsTeamMeeting() {
        /* 팀 식별자 100의 회의를 반환하는 상세 조회 서비스를 준비한다. */
        MeetingDetailQueryService service = service(Optional.of(meeting()));

        /* 비참석자 99번이 같은 팀 LEADER 권한으로 상세 조회한다. */
        MeetingDetailResult result = service.getMeetingDetail(
                new GetMeetingDetailQuery(10L, 99L, 100L, "LEADER", false, 91L)
        );

        /* 같은 팀 열람 권한으로 회의 한 건이 정상 반환돼야 한다. */
        assertThat(result.meetingId()).isEqualTo(91L);
    }

    /* 일반 비참석자가 같은 회사의 다른 회의를 읽지 못하는지 검증한다. */
    @Test
    @DisplayName("열람 범위 밖의 구성원은 MT-011로 거절한다")
    void rejectsMemberOutsideReadScope() {
        /* 회의는 존재하지만 요청자가 참석자·개설자·같은 팀 LEADER가 아닌 서비스를 준비한다. */
        MeetingDetailQueryService service = service(Optional.of(meeting()));

        /* 일반 비참석자의 상세 조회가 외부 표시 Port 호출 전에 거절되는지 검증한다. */
        assertErrorCode(
                () -> service.getMeetingDetail(
                        new GetMeetingDetailQuery(10L, 99L, 999L, "MEMBER", false, 91L)
                ),
                "MT-011"
        );
    }

    /* 타 회사 또는 없는 회의의 존재 여부가 404로 숨겨지는지 검증한다. */
    @Test
    @DisplayName("회사 범위에서 찾지 못한 회의는 MT-001로 숨긴다")
    void hidesMissingOrOtherCompanyMeeting() {
        /* 회사 범위 조회가 빈 결과를 반환하는 상세 조회 서비스를 준비한다. */
        MeetingDetailQueryService service = service(Optional.empty());

        /* 권한 판정 전에 존재하지 않는 회의 오류가 반환돼야 한다. */
        assertErrorCode(
                () -> service.getMeetingDetail(
                        new GetMeetingDetailQuery(10L, 3L, 100L, "OWNER", false, 91L)
                ),
                "MT-001"
        );
    }

    /* 지정한 회의 Optional과 미확정 액션 0건·요약 정상인 기본 Port 대역으로 MEET-04 서비스를 만든다. */
    private MeetingDetailQueryService service(Optional<MeetingDetailSnapshot> meeting) {
        /* 대부분의 테스트는 액션·요약 신호 자체를 검증하지 않으므로 기본값 대역을 사용한다. */
        return service(meeting, noPendingActionPort(), notStalledSummaryPort());
    }

    /* 지정한 회의 Optional과 액션·요약 Port 대역으로 MEET-04 서비스를 만든다. */
    private MeetingDetailQueryService service(
            Optional<MeetingDetailSnapshot> meeting,
            ActionQueryPort actionQueryPort,
            SummaryStatusQueryPort summaryStatusQueryPort
    ) {
        /* 회사 범위 회의 상세를 그대로 반환하는 저장소 대역을 만든다. */
        MeetingDetailRepository meetingRepository = (companyId, meetingId) -> meeting;

        /* 프로젝트 존재 검증과 표시 정보 조회를 제공하는 C Port 대역을 만든다. */
        ProjectQueryPort projectPort = new ProjectQueryPort() {
            /* 이 테스트는 회의 개설을 다루지 않으므로 활성 존재 검증은 사용하지 않는다. */
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                /* 호출되면 테스트 대상 경계가 잘못된 것이므로 명시적으로 실패한다. */
                throw new AssertionError("MEET-04는 활성 프로젝트 검증을 호출하지 않습니다.");
            }

            /* 과거 이력을 포함한 프로젝트 표시 정보 한 건을 반환한다. */
            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                /* 상세 응답 조립에 필요한 태그·이름·색상을 제공한다. */
                return List.of(new ProjectSnapshot(
                        12L,
                        "acommerce",
                        "A커머스 온보딩",
                        "#5B5BD6"
                ));
            }
        };

        /* 활성 검증과 과거 표시 조회를 분리한 D 회의실 Port 대역을 만든다. */
        MeetingRoomQueryPort meetingRoomPort = new MeetingRoomQueryPort() {
            /* 상세 조회는 회의실 활성 상태에 의존하지 않으므로 활성 조회를 사용하지 않는다. */
            @Override
            public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
                /* 호출되면 비활성 회의실 이력 계약을 위반한 것이므로 실패한다. */
                throw new AssertionError("MEET-04는 활성 회의실 검증을 호출하지 않습니다.");
            }

            /* 회의가 참조하는 회의실의 이름과 위치를 반환한다. */
            @Override
            public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
                /* 응답 조립에 필요한 회의실 표시 정보 한 건을 제공한다. */
                return List.of(new MeetingRoomSnapshot(
                        2L,
                        "회의실 B",
                        "박애관 422호",
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                ));
            }
        };

        /* 과거 회의 상세가 삭제 구성원을 포함하는 B Port 계약만 사용하는지 검증하는 대역을 만든다. */
        MemberQueryPort memberPort = new MemberQueryPort() {
            /* MEET-04가 활성 구성원 전용 계약으로 회귀하면 즉시 실패시킨다. */
            @Override
            public List<MemberSnapshot> findActiveMembers(Long companyId, List<Long> memberIds) {
                /* 과거 회의 참석자는 퇴사 후에도 보여야 하므로 이 경로를 사용하면 안 된다. */
                throw new AssertionError("MEET-04는 활성 구성원 전용 조회를 호출하지 않습니다.");
            }

            /* 개설자와 퇴사 가능성이 있는 두 참석자의 과거 표시 정보를 일괄 반환한다. */
            @Override
            public List<MemberSnapshot> findMembersIncludingDeleted(Long companyId, List<Long> memberIds) {
                /* 상세 응답 조립에 필요한 이름·팀·직급 표시 정보를 제공한다. */
                return List.of(
                        new MemberSnapshot(3L, "지우", 100L, "기획", "팀장"),
                        new MemberSnapshot(7L, "이든", 200L, "개발", "시니어"),
                        new MemberSnapshot(11L, "하린", 300L, "디자인", "디자이너")
                );
            }
        };

        /* 여섯 경계 대역을 주입한 실제 상세 조회 서비스를 반환한다. */
        return new MeetingDetailQueryService(
                meetingRepository,
                projectPort,
                meetingRoomPort,
                memberPort,
                actionQueryPort,
                summaryStatusQueryPort
        );
    }

    /* 미확정 액션이 없다고 답하는 C Port 대역을 만든다. */
    private ActionQueryPort noPendingActionPort() {
        return new ActionQueryPort() {
            /* MEET-04는 액션 팀 일치 검증 계약을 호출하지 않는다. */
            @Override
            public Optional<ActionTeamReference> findActionTeamReference(Long companyId, Long actionId) {
                throw new AssertionError("MEET-04는 액션 팀 조회를 호출하지 않습니다.");
            }

            /* 결과가 비어 있으면 서비스가 0건으로 해석해야 한다. */
            @Override
            public List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                    Long companyId,
                    List<Long> meetingIds
            ) {
                return List.of();
            }

            /* MEET-04는 목록 카드용 액션 수 배치 계약을 호출하지 않는다. */
            @Override
            public List<MeetingActionCount> countActionsByMeetings(Long companyId, List<Long> meetingIds) {
                throw new AssertionError("MEET-04는 액션 수 배치 조회를 호출하지 않습니다.");
            }
        };
    }

    /* 요청한 회의 ID에 미확정 액션 건수를 보고하는 C Port 대역을 만든다. */
    private ActionQueryPort pendingActionPort(Long meetingId, long undispatchedCount) {
        return new ActionQueryPort() {
            @Override
            public Optional<ActionTeamReference> findActionTeamReference(Long companyId, Long actionId) {
                throw new AssertionError("MEET-04는 액션 팀 조회를 호출하지 않습니다.");
            }

            /* 계약상 미확정 건수가 있는 회의만 결과에 실려 온다. */
            @Override
            public List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                    Long companyId,
                    List<Long> meetingIds
            ) {
                return List.of(new UndispatchedActionMeeting(meetingId, undispatchedCount));
            }

            @Override
            public List<MeetingActionCount> countActionsByMeetings(Long companyId, List<Long> meetingIds) {
                throw new AssertionError("MEET-04는 액션 수 배치 조회를 호출하지 않습니다.");
            }
        };
    }

    /* 호출되면 즉시 실패하는 C Port 대역이다 — 종료 전 회의에서 호출되지 않아야 함을 검증한다. */
    private ActionQueryPort neverCalledActionPort() {
        return new ActionQueryPort() {
            @Override
            public Optional<ActionTeamReference> findActionTeamReference(Long companyId, Long actionId) {
                throw new AssertionError("MEET-04는 액션 팀 조회를 호출하지 않습니다.");
            }

            @Override
            public List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                    Long companyId,
                    List<Long> meetingIds
            ) {
                throw new AssertionError("종료 전 회의는 미확정 액션 배치 조회를 호출하면 안 됩니다.");
            }

            @Override
            public List<MeetingActionCount> countActionsByMeetings(Long companyId, List<Long> meetingIds) {
                throw new AssertionError("MEET-04는 액션 수 배치 조회를 호출하지 않습니다.");
            }
        };
    }

    /* 요약이 중단되지 않았다고 답하는 A Port 대역을 만든다. */
    private SummaryStatusQueryPort notStalledSummaryPort() {
        /* 계약상 중단·실패가 아닌 회의는 결과에 아예 실리지 않는다. */
        return (companyId, meetingIds) -> List.of();
    }

    /* 요청한 회의 ID의 요약이 중단됐다고 답하는 A Port 대역을 만든다. */
    private SummaryStatusQueryPort stalledSummaryPort(Long meetingId) {
        return (companyId, meetingIds) ->
                List.of(new SummaryStatusQueryPort.StalledSummaryMeeting(meetingId, true));
    }

    /* 호출되면 즉시 실패하는 A Port 대역이다 — 종료 전 회의에서 호출되지 않아야 함을 검증한다. */
    private SummaryStatusQueryPort neverCalledSummaryStatusPort() {
        return (companyId, meetingIds) -> {
            throw new AssertionError("종료 전 회의는 요약 상태 배치 조회를 호출하면 안 됩니다.");
        };
    }

    /* 명세 예시와 동일한 DONE 상태 회의 메타와 참석자 식별자를 가진 상세 조회 모델을 만든다. */
    private MeetingDetailSnapshot meeting() {
        return meeting(MeetingStatus.DONE);
    }

    /* 지정한 상태로, 액션·요약 Port 호출 여부를 가르는 상세 조회 모델을 만든다. */
    private MeetingDetailSnapshot meeting(MeetingStatus status) {
        /* 아직 끝나지 않은 회의는 실제 종료 시각이 없다. */
        LocalDateTime endedAt = status == MeetingStatus.DONE
                ? LocalDateTime.of(2026, 8, 4, 15, 2, 40)
                : null;

        return new MeetingDetailSnapshot(
                91L,
                10L,
                12L,
                100L,
                2L,
                3L,
                "A커머스 온보딩 킥오프",
                status,
                LocalDateTime.of(2026, 8, 4, 14, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0),
                LocalDateTime.of(2026, 8, 4, 13, 58, 12),
                endedAt,
                true,
                LocalDateTime.of(2026, 8, 1, 10, 12),
                List.of(7L, 3L, 11L)
        );
    }

    /* 실행 결과가 예상한 서비스 오류 코드인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* BusinessException의 외부 계약 코드를 추출해 지정 코드와 비교한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
