package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.query.GetPendingActionMeetingsQuery;
import com.module06.backend.meeting.application.result.PendingActionMeetingListResult;
import com.module06.backend.meeting.domain.repository.PendingActionMeetingRepository;

/*
 * MEET-10 서비스의 후보·판정 교집합, 배치 1회 호출, 빈 결과 단축 경로를 검증한다.
 */
@DisplayName("MEET-10 확정 대기 회의 조회 서비스")
class PendingActionMeetingQueryServiceTest {

    /* 액션 도메인이 인정한 회의만 남기고 대기 건수와 프로젝트가 조립되는지 검증한다. */
    @Test
    @DisplayName("액션 도메인이 반환한 회의만 남기고 대기 건수를 조립한다")
    void keepsOnlyMeetingsReturnedByActionDomain() {
        /* host의 종료 회의 세 건 중 두 건에만 분배 대기 액션이 남은 상황을 준비한다. */
        RecordingPendingActionMeetingRepository repository = new RecordingPendingActionMeetingRepository(List.of(
                candidate(13L, 12L, "주간 백엔드 회의", LocalDateTime.of(2026, 8, 7, 14, 0)),
                candidate(11L, 12L, "스프린트 리뷰", LocalDateTime.of(2026, 8, 6, 14, 0)),
                candidate(9L, 15L, "이미 분배한 회의", LocalDateTime.of(2026, 8, 5, 14, 0))
        ));
        RecordingActionQueryPort actionQueryPort = new RecordingActionQueryPort(List.of(
                new ActionQueryPort.UndispatchedActionMeeting(13L, 3L),
                new ActionQueryPort.UndispatchedActionMeeting(11L, 1L)
        ));
        PendingActionMeetingQueryService service = new PendingActionMeetingQueryService(
                repository,
                actionQueryPort,
                projectPort()
        );

        /* 로그인 사용자 3번의 확정 대기 회의를 조회한다. */
        PendingActionMeetingListResult result = service.getPendingActionMeetings(
                new GetPendingActionMeetingsQuery(10L, 3L)
        );

        /* 액션 도메인이 반환하지 않은 9번 회의는 목록에서 빠져야 한다. */
        assertThat(result.meetings())
                .extracting(PendingActionMeetingListResult.MeetingItem::meetingId)
                .containsExactly(13L, 11L);

        /* 회의별 분배 대기 건수가 액션 도메인 판정값 그대로 실려야 한다. */
        assertThat(result.meetings())
                .extracting(PendingActionMeetingListResult.MeetingItem::pendingActionCount)
                .containsExactly(3L, 1L);

        /* 프로젝트 표시 정보가 식별자 기준으로 올바르게 붙어야 한다. */
        assertThat(result.meetings().get(0).project().tag()).isEqualTo("Z-GROUPWARE");

        /* 저장소에는 인증 principal의 회사와 구성원 식별자가 그대로 전달돼야 한다. */
        assertThat(repository.capturedCompanyId).isEqualTo(10L);
        assertThat(repository.capturedHostMemberId).isEqualTo(3L);
    }

    /* 회의별 반복 호출 없이 액션 Port가 배치로 한 번만 호출되는지 검증한다. */
    @Test
    @DisplayName("액션 Port를 회의별로 반복 호출하지 않고 한 번만 호출한다")
    void callsActionPortOnceInBatch() {
        /* 후보 회의를 세 건 준비해 반복 호출 여부를 관찰한다. */
        RecordingActionQueryPort actionQueryPort = new RecordingActionQueryPort(List.of(
                new ActionQueryPort.UndispatchedActionMeeting(13L, 2L)
        ));
        PendingActionMeetingQueryService service = new PendingActionMeetingQueryService(
                new RecordingPendingActionMeetingRepository(List.of(
                        candidate(13L, 12L, "회의 1", LocalDateTime.of(2026, 8, 7, 14, 0)),
                        candidate(12L, 12L, "회의 2", LocalDateTime.of(2026, 8, 6, 14, 0)),
                        candidate(11L, 12L, "회의 3", LocalDateTime.of(2026, 8, 5, 14, 0))
                )),
                actionQueryPort,
                projectPort()
        );

        /* 세 건의 후보로 확정 대기 목록을 조회한다. */
        service.getPendingActionMeetings(new GetPendingActionMeetingsQuery(10L, 3L));

        /* 후보가 3건이어도 액션 Port 호출은 한 번이어야 N+1이 아니다. */
        assertThat(actionQueryPort.invocationCount).isEqualTo(1);

        /* 한 번의 호출에 후보 회의 식별자 전체가 담겨야 한다. */
        assertThat(actionQueryPort.capturedMeetingIds).containsExactly(13L, 12L, 11L);
    }

    /* 후보가 배치 임계치를 넘어도 호출자가 목록을 자르지 않는지 검증한다. */
    @Test
    @DisplayName("후보가 201건이어도 자르지 않고 전체를 한 번에 전달한다")
    void doesNotSplitCandidatesAboveBatchThreshold() {
        /* 액션 도메인이 내부에서 분할하므로 호출자는 크기와 무관하게 전체를 넘겨야 한다. */
        List<PendingActionMeetingRepository.PendingActionMeetingCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            /* 최근 시작 순 정렬을 유지하도록 식별자와 시각을 함께 내림차순으로 만든다. */
            candidates.add(candidate(
                    (long) (300 - index),
                    12L,
                    "회의 " + index,
                    LocalDateTime.of(2026, 8, 7, 14, 0).minusMinutes(index)
            ));
        }
        RecordingActionQueryPort actionQueryPort = new RecordingActionQueryPort(List.of(
                new ActionQueryPort.UndispatchedActionMeeting(300L, 1L)
        ));
        PendingActionMeetingQueryService service = new PendingActionMeetingQueryService(
                new RecordingPendingActionMeetingRepository(candidates),
                actionQueryPort,
                projectPort()
        );

        /* 201건의 후보로 확정 대기 목록을 조회한다. */
        service.getPendingActionMeetings(new GetPendingActionMeetingsQuery(10L, 3L));

        /* 200건 단위로 나누면 2회가 되므로 1회여야 분할이 없는 것이다. */
        assertThat(actionQueryPort.invocationCount).isEqualTo(1);

        /* 잘린 조각이 아니라 후보 201건 전체가 한 번에 전달돼야 한다. */
        assertThat(actionQueryPort.capturedMeetingIds).hasSize(201);
    }

    /* 후보가 없으면 외부 Port를 전혀 호출하지 않는 단축 경로를 검증한다. */
    @Test
    @DisplayName("후보 회의가 없으면 액션·프로젝트 Port를 호출하지 않는다")
    void skipsPortsWhenNoCandidate() {
        /* host인 종료 회의가 하나도 없는 상황을 준비한다. */
        PendingActionMeetingQueryService service = new PendingActionMeetingQueryService(
                new RecordingPendingActionMeetingRepository(List.of()),
                throwingActionPort(),
                throwingProjectPort()
        );

        /* 후보가 없는 사용자의 확정 대기 목록을 조회한다. */
        PendingActionMeetingListResult result = service.getPendingActionMeetings(
                new GetPendingActionMeetingsQuery(10L, 3L)
        );

        /* 외부 Port 호출 없이 빈 목록이 정상 결과로 반환돼야 한다. */
        assertThat(result.meetings()).isEmpty();
    }

    /* 액션 도메인이 아무 회의도 인정하지 않으면 프로젝트 조회를 생략하는지 검증한다. */
    @Test
    @DisplayName("분배 대기 회의가 없으면 프로젝트 Port를 호출하지 않는다")
    void skipsProjectPortWhenNoPendingMeeting() {
        /* 후보는 있지만 액션 도메인이 빈 결과를 반환하는 상황을 준비한다. */
        PendingActionMeetingQueryService service = new PendingActionMeetingQueryService(
                new RecordingPendingActionMeetingRepository(List.of(
                        candidate(13L, 12L, "이미 분배한 회의", LocalDateTime.of(2026, 8, 7, 14, 0))
                )),
                new RecordingActionQueryPort(List.of()),
                throwingProjectPort()
        );

        /* 분배 대기 액션이 없는 사용자의 확정 대기 목록을 조회한다. */
        PendingActionMeetingListResult result = service.getPendingActionMeetings(
                new GetPendingActionMeetingsQuery(10L, 3L)
        );

        /* 프로젝트 조회 없이 빈 목록이 정상 결과로 반환돼야 한다. */
        assertThat(result.meetings()).isEmpty();
    }

    /* 요청하지 않은 회의 식별자가 돌아와도 후보와의 교집합만 남기는지 검증한다. */
    @Test
    @DisplayName("요청하지 않은 회의 식별자가 돌아와도 후보와의 교집합만 사용한다")
    void ignoresMeetingIdsNotRequested() {
        /* 액션 도메인이 후보에 없는 99번 회의를 함께 반환하는 상황을 준비한다. */
        PendingActionMeetingQueryService service = new PendingActionMeetingQueryService(
                new RecordingPendingActionMeetingRepository(List.of(
                        candidate(13L, 12L, "주간 백엔드 회의", LocalDateTime.of(2026, 8, 7, 14, 0))
                )),
                new RecordingActionQueryPort(List.of(
                        new ActionQueryPort.UndispatchedActionMeeting(13L, 2L),
                        new ActionQueryPort.UndispatchedActionMeeting(99L, 5L)
                )),
                projectPort()
        );

        /* 확정 대기 목록을 조회한다. */
        PendingActionMeetingListResult result = service.getPendingActionMeetings(
                new GetPendingActionMeetingsQuery(10L, 3L)
        );

        /* 후보에 없던 99번 회의는 응답에 포함되지 않아야 한다. */
        assertThat(result.meetings())
                .extracting(PendingActionMeetingListResult.MeetingItem::meetingId)
                .containsExactly(13L);
    }

    /* 대기 건수가 0 이하인 회의는 목록에서 제외되는지 검증한다. */
    @Test
    @DisplayName("분배 대기 건수가 0인 회의는 제외한다")
    void excludesMeetingWithZeroCount() {
        /* 액션 도메인이 건수 0으로 반환한 회의를 포함한 상황을 준비한다. */
        PendingActionMeetingQueryService service = new PendingActionMeetingQueryService(
                new RecordingPendingActionMeetingRepository(List.of(
                        candidate(13L, 12L, "대기 있음", LocalDateTime.of(2026, 8, 7, 14, 0)),
                        candidate(11L, 12L, "대기 없음", LocalDateTime.of(2026, 8, 6, 14, 0))
                )),
                new RecordingActionQueryPort(List.of(
                        new ActionQueryPort.UndispatchedActionMeeting(13L, 1L),
                        new ActionQueryPort.UndispatchedActionMeeting(11L, 0L)
                )),
                projectPort()
        );

        /* 확정 대기 목록을 조회한다. */
        PendingActionMeetingListResult result = service.getPendingActionMeetings(
                new GetPendingActionMeetingsQuery(10L, 3L)
        );

        /* 건수가 0인 회의는 처리할 일이 없으므로 목록에서 빠져야 한다. */
        assertThat(result.meetings())
                .extracting(PendingActionMeetingListResult.MeetingItem::meetingId)
                .containsExactly(13L);
    }

    /* 인증 식별자가 없으면 저장소 조회 전에 거절하는지 검증한다. */
    @Test
    @DisplayName("인증 식별자가 없으면 Z-001로 거절한다")
    void rejectsMissingPrincipal() {
        /* 어떤 외부 호출도 허용하지 않는 대역으로 서비스를 구성한다. */
        PendingActionMeetingQueryService service = new PendingActionMeetingQueryService(
                new RecordingPendingActionMeetingRepository(List.of()),
                throwingActionPort(),
                throwingProjectPort()
        );

        /* 구성원 식별자가 없는 Query는 공통 입력값 오류로 거절돼야 한다. */
        assertThatThrownBy(() -> service.getPendingActionMeetings(
                new GetPendingActionMeetingsQuery(10L, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("입력값");
    }

    /* 테스트 후보 회의 읽기 모델을 만든다. */
    private PendingActionMeetingRepository.PendingActionMeetingCandidate candidate(
            Long meetingId,
            Long projectId,
            String title,
            LocalDateTime startAt
    ) {
        /* 저장소가 반환하는 최소 회의 메타를 그대로 구성한다. */
        return new PendingActionMeetingRepository.PendingActionMeetingCandidate(
                meetingId,
                projectId,
                title,
                startAt
        );
    }

    /* 요청 프로젝트의 표시 정보를 반환하는 테스트 대역을 만든다. */
    private ProjectQueryPort projectPort() {
        /* MEET-01 존재 확인과 MEET-10 배치 표시 조회를 모두 구현한 테스트 대역을 반환한다. */
        return new ProjectQueryPort() {
            /* 이 테스트에서는 프로젝트 존재 확인 단건 계약을 사용하지 않는다. */
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                /* 준비된 프로젝트는 모두 정상이라고 가정한다. */
                return true;
            }

            /* 요청한 프로젝트 식별자에 대응하는 표시 정보를 반환한다. */
            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                /* 서비스가 식별자 기준으로 조립하는지 확인할 수 있도록 요청 순서를 뒤집어 반환한다. */
                List<ProjectSnapshot> snapshots = new ArrayList<>();
                for (Long projectId : projectIds) {
                    snapshots.add(new ProjectSnapshot(projectId, "Z-GROUPWARE", "잇다 그룹웨어", "#5B5BD6"));
                }
                java.util.Collections.reverse(snapshots);
                return List.copyOf(snapshots);
            }
        };
    }

    /* 호출되면 테스트를 실패시키는 액션 Port 대역을 만든다. */
    private ActionQueryPort throwingActionPort() {
        /* 단축 경로에서는 액션 도메인 조회가 호출되지 않아야 한다. */
        return new ActionQueryPort() {
            /* 액션 존재 확인이 호출되면 테스트를 즉시 실패시킨다. */
            @Override
            public boolean existsAction(Long companyId, Long actionId) {
                return fail("MEET-10 단축 경로에서 액션 존재 확인이 호출되면 안 된다.");
            }

            /* 분배 대기 배치 조회가 호출되면 테스트를 즉시 실패시킨다. */
            @Override
            public List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                    Long companyId,
                    List<Long> meetingIds
            ) {
                return fail("후보 회의가 없으면 액션 배치 조회를 호출하면 안 된다.");
            }
        };
    }

    /* 호출되면 테스트를 실패시키는 프로젝트 Port 대역을 만든다. */
    private ProjectQueryPort throwingProjectPort() {
        /* 단축 경로에서는 프로젝트 표시 조회가 호출되지 않아야 한다. */
        return new ProjectQueryPort() {
            /* 프로젝트 존재 확인이 호출되면 테스트를 즉시 실패시킨다. */
            @Override
            public boolean existsActiveProject(Long companyId, Long projectId) {
                return fail("MEET-10 단축 경로에서 프로젝트 존재 확인이 호출되면 안 된다.");
            }

            /* 프로젝트 배치 조회가 호출되면 테스트를 즉시 실패시킨다. */
            @Override
            public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
                return fail("분배 대기 회의가 없으면 프로젝트 배치 조회를 호출하면 안 된다.");
            }
        };
    }

    /* 서비스가 전달한 인증 식별자를 기록하고 준비된 후보를 반환하는 저장소 대역이다. */
    private static final class RecordingPendingActionMeetingRepository
            implements PendingActionMeetingRepository {

        /* 서비스에 반환할 후보 회의 읽기 모델 목록이다. */
        private final List<PendingActionMeetingCandidate> candidates;

        /* 서비스가 저장소에 전달한 회사 식별자다. */
        private Long capturedCompanyId;

        /* 서비스가 저장소에 전달한 host 구성원 식별자다. */
        private Long capturedHostMemberId;

        /* 테스트가 지정한 후보 회의 목록으로 저장소 대역을 생성한다. */
        private RecordingPendingActionMeetingRepository(List<PendingActionMeetingCandidate> candidates) {
            /* 외부 변경이 테스트 결과에 영향을 주지 않도록 목록을 복사한다. */
            this.candidates = List.copyOf(candidates);
        }

        /* 전달받은 인증 식별자를 기록하고 준비된 후보 회의를 반환한다. */
        @Override
        public List<PendingActionMeetingCandidate> findHostedDoneMeetings(Long companyId, Long hostMemberId) {
            /* 인증 principal 값이 그대로 저장소까지 전달됐는지 확인할 수 있도록 기록한다. */
            this.capturedCompanyId = companyId;
            this.capturedHostMemberId = hostMemberId;
            return candidates;
        }
    }

    /* 배치 호출 횟수와 전달된 회의 식별자를 기록하는 액션 Port 대역이다. */
    private static final class RecordingActionQueryPort implements ActionQueryPort {

        /* 서비스에 반환할 분배 대기 판정 결과다. */
        private final List<UndispatchedActionMeeting> undispatchedMeetings;

        /* 배치 조회가 호출된 횟수다. */
        private int invocationCount;

        /* 서비스가 배치로 전달한 회의 식별자 목록이다. */
        private final List<Long> capturedMeetingIds = new ArrayList<>();

        /* 테스트가 지정한 판정 결과로 액션 Port 대역을 생성한다. */
        private RecordingActionQueryPort(List<UndispatchedActionMeeting> undispatchedMeetings) {
            /* 외부 변경이 테스트 결과에 영향을 주지 않도록 목록을 복사한다. */
            this.undispatchedMeetings = List.copyOf(undispatchedMeetings);
        }

        /* MEET-10 테스트에서는 액션 존재 확인 단건 계약을 사용하지 않는다. */
        @Override
        public boolean existsAction(Long companyId, Long actionId) {
            /* 호출되지 않는 기존 계약을 정상 값으로 만족시킨다. */
            return true;
        }

        /* 호출 횟수와 전달된 회의 식별자를 기록하고 준비된 판정 결과를 반환한다. */
        @Override
        public List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
                Long companyId,
                List<Long> meetingIds
        ) {
            /* 회의별 반복 호출(N+1)이 아닌지 검증할 수 있도록 호출 횟수를 센다. */
            this.invocationCount++;
            this.capturedMeetingIds.addAll(meetingIds);
            return undispatchedMeetings;
        }
    }
}
