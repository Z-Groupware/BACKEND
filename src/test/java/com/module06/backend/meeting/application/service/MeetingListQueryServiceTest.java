package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.port.out.SummaryStatusQueryPort;
import com.module06.backend.meeting.application.query.GetMeetingListQuery;
import com.module06.backend.meeting.application.result.MeetingListResult;
import com.module06.backend.meeting.domain.model.MeetingListScope;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingTopicType;
import com.module06.backend.meeting.domain.repository.MeetingListRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingAttendeeReference;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingSnapshot;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingTopicSnapshot;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.ProjectMeetingSnapshot;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.UpcomingMeetingSnapshot;

/*
 * MEET-02 서비스의 기본 기간·페이지·외부 표시값 조합과 오류 계약을 검증한다.
 */
@DisplayName("MEET-02 회의 목록 조회 서비스")
class MeetingListQueryServiceTest {

    /* 현재 시각을 2026년 8월 7일 09시 KST로 고정하는 테스트 시계다. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-07T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* 기본 기간과 페이지가 적용되고 회의실·프로젝트·액션 수·참석자가 배치 조립되는지 검증한다. */
    @Test
    @DisplayName("기본 최근 3개월 회의에 회의실·프로젝트·참석자 표시값을 조립한다")
    void appliesDefaultsAndAssemblesMeetingList() {
        /* host 3번이 개설한 회의 두 건과 전체 37건인 저장소 페이지를 준비한다. */
        RecordingMeetingListRepository repository = new RecordingMeetingListRepository(
                new MeetingListRepository.MeetingPage(
                        List.of(
                                meeting(92L, 13L, 4L, "최근 회의", 8, 6, 3L, List.of(3L, 7L)),
                                meeting(91L, 12L, 2L, "이전 회의", 8, 4, 3L, List.of(3L, 7L, 11L, 15L))
                        ),
                        37L,
                        2
                )
        );
        RecordingActionQueryPort actionQueryPort = new RecordingActionQueryPort(List.of(
                new ActionQueryPort.MeetingActionCount(92L, 3L),
                new ActionQueryPort.MeetingActionCount(999L, 7L)
        ));
        RecordingMemberQueryPort memberQueryPort = new RecordingMemberQueryPort(List.of(
                new MemberQueryPort.MemberSnapshot(3L, "지우", 1L, "기획"),
                new MemberQueryPort.MemberSnapshot(7L, "이든", 2L, "개발"),
                new MemberQueryPort.MemberSnapshot(11L, "소민", 2L, "개발"),
                new MemberQueryPort.MemberSnapshot(15L, "다인", 2L, "개발")
        ));
        MeetingListQueryService service = new MeetingListQueryService(
                repository,
                meetingRoomPort(),
                projectPort(),
                actionQueryPort,
                summaryStatusPort(),
                meetingQueryRepository(),
                memberQueryPort,
                FIXED_CLOCK
        );

        /* 기간과 페이지를 모두 생략한 개설자 본인(OWNER)의 회사 전체 조회를 실행한다. */
        MeetingListResult result = service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, null, null, null, null, null, null, null
        ));

        /* 생략 기간은 2026-05-07 00시부터 2026-08-07 마지막 순간까지여야 한다. */
        assertThat(repository.criteria.fromInclusive()).isEqualTo(LocalDateTime.of(2026, 5, 7, 0, 0));
        assertThat(repository.criteria.toInclusive().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 7));

        /* 생략 페이지 값은 0번 페이지와 20건으로, scope 생략은 null로 저장소에 전달돼야 한다. */
        assertThat(repository.criteria.page()).isZero();
        assertThat(repository.criteria.size()).isEqualTo(20);
        assertThat(repository.criteria.companyWideRead()).isTrue();
        assertThat(repository.criteria.scope()).isNull();

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
        assertThat(result.meetings())
                .extracting(item -> item.summaryStatus() == null ? null : item.summaryStatus().name())
                .containsExactly(null, "STALLED");
        assertThat(result.meetings())
                .extracting(MeetingListResult.MeetingItem::teamId)
                .containsExactly(null, null);
        assertThat(result.meetings())
                .extracting(MeetingListResult.MeetingItem::originLabel)
                .containsExactly("OWNER", "OWNER");
        assertThat(result.meetings().get(0).agendaPreview().mainTopic()).isEqualTo("Main agenda");
        assertThat(result.meetings().get(0).agendaPreview().firstSubTopic()).isEqualTo("First sub agenda");
        assertThat(result.page().totalElements()).isEqualTo(37L);
        assertThat(result.page().totalPages()).isEqualTo(2);

        /* 회의 식별자에 맞는 회의실 이름과 프로젝트 태그가 배치 결과에서 조립돼야 한다. */
        assertThat(result.meetings().get(0).meetingRoom().name()).isEqualTo("회의실 D");
        assertThat(result.meetings().get(1).project().tag()).isEqualTo("acommerce");

        /* 요청자가 두 회의 모두의 host이므로 isHost는 true, 이미 끝난 회의라 entryAvailable은 false여야 한다. */
        assertThat(result.meetings())
                .extracting(MeetingListResult.MeetingItem::isHost)
                .containsExactly(true, true);
        assertThat(result.meetings())
                .extracting(MeetingListResult.MeetingItem::entryAvailable)
                .containsExactly(false, false);
        assertThat(result.meetings())
                .extracting(MeetingListResult.MeetingItem::durationMinutes)
                .containsExactly(60, 60);

        /* 참석자는 저장소가 반환한 memberId 순서 그대로 이름이 채워져야 한다. */
        assertThat(result.meetings().get(0).attendees())
                .extracting(MeetingListResult.Attendee::memberId, MeetingListResult.Attendee::name)
                .containsExactly(tuple(3L, "지우"), tuple(7L, "이든"));
        assertThat(result.meetings().get(1).attendees())
                .extracting(MeetingListResult.Attendee::memberId)
                .containsExactly(3L, 7L, 11L, 15L);

        /* 액션 도메인은 현재 페이지 회의 ID만 회사 범위와 함께 정확히 한 번 호출해야 한다. */
        assertThat(actionQueryPort.companyId).isEqualTo(10L);
        assertThat(actionQueryPort.meetingIds).containsExactly(92L, 91L);
        assertThat(actionQueryPort.callCount).isEqualTo(1);

        /* 구성원 도메인은 페이지 전체에서 중복 제거된 참석자 ID로 정확히 한 번만 호출해야 한다. */
        assertThat(memberQueryPort.callCount).isEqualTo(1);
        assertThat(memberQueryPort.companyId).isEqualTo(10L);
        assertThat(memberQueryPort.memberIds).containsExactly(3L, 7L, 11L, 15L);
    }

    /* host가 아닌 요청자의 isHost·entryAvailable·durationMinutes 계산을 검증한다. */
    @Test
    @DisplayName("host가 아닌 요청자에게는 isHost=false, 진행 중 회의는 entryAvailable=true를 계산한다")
    void computesHostEntryAndDurationForNonHostRequester() {
        /* host는 99번이고 요청자 3번은 참석자로만 등록된, 지금 진행 중인 35분 회의를 준비한다. */
        LocalDateTime startAt = LocalDateTime.of(2026, 8, 7, 8, 55);
        RecordingMeetingListRepository repository = new RecordingMeetingListRepository(
                new MeetingListRepository.MeetingPage(
                        List.of(new MeetingListRepository.MeetingListSnapshot(
                                50L, 12L, null, 2L, "진행 중 회의", MeetingStatus.IN_PROGRESS,
                                startAt, startAt.plusMinutes(35), false, 99L, List.of(99L, 3L)
                        )),
                        1L,
                        1
                )
        );
        RecordingMemberQueryPort memberQueryPort = new RecordingMemberQueryPort(List.of(
                new MemberQueryPort.MemberSnapshot(99L, "김서준", 1L, "기획"),
                new MemberQueryPort.MemberSnapshot(3L, "지우", 1L, "기획")
        ));
        MeetingListQueryService service = new MeetingListQueryService(
                repository,
                meetingRoomPort(),
                projectPort(),
                new RecordingActionQueryPort(List.of()),
                summaryStatusPort(),
                meetingQueryRepository(),
                memberQueryPort,
                FIXED_CLOCK
        );

        MeetingListResult result = service.getMeetings(new GetMeetingListQuery(
                10L, 3L, false, null, null, null, null, null, null, null, null
        ));

        /* 요청자가 host가 아니므로 isHost는 false, 지금 시각이 회의 진행 구간 안이므로 entryAvailable은 true다. */
        MeetingListResult.MeetingItem item = result.meetings().get(0);
        assertThat(item.isHost()).isFalse();
        assertThat(item.entryAvailable()).isTrue();
        assertThat(item.durationMinutes()).isEqualTo(35);
    }

    /* 비대면 회의의 nullable 예약 정보를 MEET-02 결과로 안전하게 변환하는지 검증한다. */
    @Test
    @DisplayName("비대면 회의는 예약 시간과 회의실 없이 목록 결과를 조립한다")
    void assemblesOnlineMeetingWithoutScheduleAndRoom() {
        /* 확정 후 저장소가 반환한 비대면 회의 한 건을 준비한다. */
        RecordingMeetingListRepository repository = new RecordingMeetingListRepository(
                new MeetingListRepository.MeetingPage(
                        List.of(new MeetingListRepository.MeetingListSnapshot(
                                70L, 12L, null, null, "비대면 회의", MeetingStatus.DONE,
                                null, null, true, 3L, List.of(3L)
                        )),
                        1L,
                        1
                )
        );
        RecordingMemberQueryPort memberQueryPort = new RecordingMemberQueryPort(List.of(
                new MemberQueryPort.MemberSnapshot(3L, "지우", 1L, "기획")
        ));
        MeetingListQueryService service = new MeetingListQueryService(
                repository,
                meetingRoomPort(),
                projectPort(),
                new RecordingActionQueryPort(List.of()),
                summaryStatusPort(),
                meetingQueryRepository(),
                memberQueryPort,
                FIXED_CLOCK
        );

        /* 회사 전체 MEET-02 목록을 조회해 비대면 카드 결과를 조립한다. */
        MeetingListResult.MeetingItem result = service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, null, null, null, null, null, 0, 20
        )).meetings().get(0);

        /* 예약 정보가 없어도 실패하지 않고 비대면 신호와 안전한 표시 기본값을 반환해야 한다. */
        assertThat(result.isOnline()).isTrue();
        assertThat(result.startAt()).isNull();
        assertThat(result.endAt()).isNull();
        assertThat(result.meetingRoom()).isNull();
        assertThat(result.durationMinutes()).isZero();
        assertThat(result.entryAvailable()).isFalse();
    }

    /* 취소되거나 일찍 끝난 회의는 예약 시간 창 안에 있어도 entryAvailable이 false여야 한다. */
    @Test
    @DisplayName("CANCELED·DONE 회의는 예약 시간 창 안에 있어도 entryAvailable=false를 계산한다")
    void restrictsEntryAvailableToScheduledAndInProgressStatuses() {
        /* MeetingEntryPolicy의 시간 창(08:45~09:30)만 보면 true가 나올 예약 구간을 그대로 쓴다. */
        LocalDateTime startAt = LocalDateTime.of(2026, 8, 7, 8, 55);
        RecordingMeetingListRepository repository = new RecordingMeetingListRepository(
                new MeetingListRepository.MeetingPage(
                        List.of(
                                new MeetingListRepository.MeetingListSnapshot(
                                        60L, 12L, null, 2L, "취소된 회의", MeetingStatus.CANCELED,
                                        startAt, startAt.plusMinutes(35), false, 3L, List.of(3L)
                                ),
                                new MeetingListRepository.MeetingListSnapshot(
                                        61L, 12L, null, 2L, "일찍 끝난 회의", MeetingStatus.DONE,
                                        startAt, startAt.plusMinutes(35), false, 3L, List.of(3L)
                                )
                        ),
                        2L,
                        1
                )
        );
        RecordingMemberQueryPort memberQueryPort = new RecordingMemberQueryPort(List.of(
                new MemberQueryPort.MemberSnapshot(3L, "지우", 1L, "기획")
        ));
        MeetingListQueryService service = new MeetingListQueryService(
                repository,
                meetingRoomPort(),
                projectPort(),
                new RecordingActionQueryPort(List.of()),
                summaryStatusPort(),
                meetingQueryRepository(),
                memberQueryPort,
                FIXED_CLOCK
        );

        MeetingListResult result = service.getMeetings(new GetMeetingListQuery(
                10L, 3L, false, null, null, null, null, null, null, null, null
        ));

        /* 상태로 먼저 막혀 시간 창과 무관하게 두 회의 모두 entryAvailable=false여야 한다. */
        assertThat(result.meetings())
                .extracting(MeetingListResult.MeetingItem::entryAvailable)
                .containsExactly(false, false);
    }

    /* scope 필터가 companyWideRead와 별개로 저장소 조건에 그대로 전달되는지 검증한다. */
    @Test
    @DisplayName("scope=HOSTED·ATTENDING을 저장소 조건에 그대로 전달한다")
    void passesScopeToRepositoryCriteria() {
        RecordingMeetingListRepository repository = new RecordingMeetingListRepository(
                new MeetingListRepository.MeetingPage(List.of(), 0L, 0)
        );
        MeetingListQueryService service = new MeetingListQueryService(
                repository,
                meetingRoomPort(),
                projectPort(),
                new RecordingActionQueryPort(List.of()),
                summaryStatusPort(),
                meetingQueryRepository(),
                neverCalledMemberPort(),
                FIXED_CLOCK
        );

        /* OWNER가 scope=HOSTED로 호출해도 역할이 아닌 scope 조건이 그대로 전달돼야 한다. */
        service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, null, null, null, null, MeetingListScope.HOSTED, null, null
        ));
        assertThat(repository.criteria.scope()).isEqualTo(MeetingListScope.HOSTED);

        service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, null, null, null, null, MeetingListScope.ATTENDING, null, null
        ));
        assertThat(repository.criteria.scope()).isEqualTo(MeetingListScope.ATTENDING);
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
                summaryStatusPort(),
                meetingQueryRepository(),
                neverCalledMemberPort(),
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
                null,
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
                summaryStatusPort(),
                meetingQueryRepository(),
                neverCalledMemberPort(),
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
                null,
                0,
                20
        )), "Z-001");

        /* 음수 페이지와 최대 100을 초과한 크기도 같은 입력 오류여야 한다. */
        assertErrorCode(() -> service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, null, null, null, null, null, -1, 20
        )), "Z-001");
        assertErrorCode(() -> service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, null, null, null, null, null, 0, 101
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
                summaryStatusPort(),
                meetingQueryRepository(),
                neverCalledMemberPort(),
                FIXED_CLOCK
        );

        /* 회사 범위에 없는 프로젝트 필터는 프로젝트 표준 not-found 오류여야 한다. */
        assertErrorCode(() -> service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, 999L, null, null, null, null, null, 0, 20
        )), "PJ-001");

        /* 프로젝트를 생략하고 없는 회의실을 지정하면 회의실 표준 not-found 오류여야 한다. */
        assertErrorCode(() -> service.getMeetings(new GetMeetingListQuery(
                10L, 3L, true, null, 999L, null, null, null, null, 0, 20
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
                        new MeetingRoomSnapshot(4L, "회의실 D", null),
                        new MeetingRoomSnapshot(2L, "회의실 B", null)
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

    /* 빈 페이지 테스트에서 참석자 배치 조회가 호출되면 즉시 실패하는 Port 대역이다. */
    private SummaryStatusQueryPort summaryStatusPort() {
        return (companyId, meetingIds) -> meetingIds.contains(91L)
                ? List.of(new SummaryStatusQueryPort.StalledSummaryMeeting(91L, true))
                : List.of();
    }

    private MeetingQueryRepository meetingQueryRepository() {
        return new MeetingQueryRepository() {
            @Override
            public Optional<MeetingSnapshot> findMeeting(Long companyId, Long meetingId) {
                return Optional.empty();
            }

            @Override
            public List<ProjectMeetingSnapshot> findProjectMeetingsOrdered(Long companyId, Long projectId) {
                return List.of();
            }

            @Override
            public Map<Long, Long> countMeetingsByProjectIds(Long companyId, List<Long> projectIds) {
                return Map.of();
            }

            @Override
            public List<UpcomingMeetingSnapshot> findUpcomingMeetings(
                    Long companyId,
                    Long memberId,
                    LocalDateTime now,
                    int limit
            ) {
                return List.of();
            }

            @Override
            public List<MeetingTopicSnapshot> findMeetingTopics(Long companyId, List<Long> meetingIds) {
                if (!meetingIds.contains(92L)) {
                    return List.of();
                }
                return List.of(
                        new MeetingTopicSnapshot(92L, 1L, null, MeetingTopicType.MAIN, "Main agenda", 1),
                        new MeetingTopicSnapshot(92L, 2L, 1L, MeetingTopicType.SUB, "First sub agenda", 2)
                );
            }

            @Override
            public List<MeetingAttendeeReference> findMeetingAttendees(Long companyId, List<Long> meetingIds) {
                return List.of();
            }
        };
    }

    private MemberQueryPort neverCalledMemberPort() {
        return new MemberQueryPort() {
            @Override
            public List<MemberSnapshot> findActiveMembers(Long companyId, List<Long> memberIds) {
                /* MEET-02는 활성 전용 계약을 호출하지 않는다. */
                throw new AssertionError("MEET-02는 활성 구성원 전용 조회를 호출하면 안 됩니다.");
            }

            @Override
            public List<MemberSnapshot> findMembersIncludingDeleted(Long companyId, List<Long> memberIds) {
                /* 빈 페이지에서는 조회할 참석자 자체가 없어야 한다. */
                throw new AssertionError("빈 페이지에서는 참석자 배치 조회를 호출하면 안 됩니다.");
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
            Long hostMemberId,
            List<Long> attendeeMemberIds
    ) {
        /* 오후 2시부터 한 시간 진행되는 완료 회의 목록 모델을 반환한다. */
        LocalDateTime startAt = LocalDateTime.of(2026, month, day, 14, 0);
        return new MeetingListRepository.MeetingListSnapshot(
                meetingId,
                projectId,
                null,
                meetingRoomId,
                title,
                MeetingStatus.DONE,
                startAt,
                startAt.plusHours(1),
                false,
                hostMemberId,
                attendeeMemberIds
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

        /* MEET-01 액션 팀 조회 계약은 MEET-02 테스트에서 사용하지 않는다. */
        @Override
        public java.util.Optional<ActionTeamReference> findActionTeamReference(Long companyId, Long actionId) {
            /* 잘못된 호출이 생기면 테스트가 즉시 실패하도록 한다. */
            throw new AssertionError("MEET-02는 액션 팀 조회를 호출하면 안 됩니다.");
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

    /* MEET-02가 참석자 이름을 페이지 단위로 한 번만 요청하는지 기록하는 Port 대역이다. */
    private static final class RecordingMemberQueryPort implements MemberQueryPort {

        /* 서비스 호출에 반환할 구성원 표시 정보 전체다. */
        private final List<MemberSnapshot> members;

        /* 실제 배치 호출 횟수와 회사·참석자 식별자를 검증하기 위한 기록이다. */
        private int callCount;
        private Long companyId;
        private List<Long> memberIds;

        /* 테스트가 지정한 구성원 표시 정보로 Port 대역을 생성한다. */
        private RecordingMemberQueryPort(List<MemberSnapshot> members) {
            /* 변경 불가능한 목록으로 복사해 테스트 도중 반환값 변형을 막는다. */
            this.members = List.copyOf(members);
        }

        /* MEET-02는 활성 전용 계약을 호출하지 않는다. */
        @Override
        public List<MemberSnapshot> findActiveMembers(Long companyId, List<Long> memberIds) {
            /* 잘못된 호출이 생기면 테스트가 즉시 실패하도록 한다. */
            throw new AssertionError("MEET-02는 활성 구성원 전용 조회를 호출하면 안 됩니다.");
        }

        /* 현재 페이지의 배치 호출 조건을 기록하고 요청된 참석자의 표시 정보를 반환한다. */
        @Override
        public List<MemberSnapshot> findMembersIncludingDeleted(Long companyId, List<Long> memberIds) {
            /* 호출 횟수와 전달값을 보관해 N+1 방지 계약을 검증한다. */
            this.callCount++;
            this.companyId = companyId;
            this.memberIds = List.copyOf(memberIds);
            return members.stream().filter(member -> memberIds.contains(member.memberId())).toList();
        }
    }
}
