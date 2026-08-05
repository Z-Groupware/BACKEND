package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.query.GetMeetingAttendeesQuery;
import com.module06.backend.meeting.application.result.MeetingAttendeesResult;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingTopicType;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingAttendeeReference;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingSnapshot;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingTopicSnapshot;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.ProjectMeetingSnapshot;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.UpcomingMeetingSnapshot;

/*
 * RESULT-01 조회 서비스의 테넌트·열람 권한·참석자 변환 규칙을 검증하는 단위 테스트다.
 */
@DisplayName("RESULT-01 회의 참석자 조회 서비스")
class MeetingQueryServiceTest {

    /* 참석자인 요청자가 개설자 우선 전체 명단을 조회할 수 있는지 검증한다. */
    @Test
    @DisplayName("참석자는 개설자가 첫 번째인 전체 참석자 명단을 조회한다")
    void attendeeReadsHostFirstRoster() {
        /* 구성원 식별자 순서가 개설자 우선이 아닌 회의 조회 결과를 준비한다. */
        MeetingQueryRepository repository = repository(Optional.of(meeting(List.of(7L, 3L, 11L))));
        MeetingQueryService service = new MeetingQueryService(repository, memberPort());

        /* 7번 참석자가 자신의 회의 명단을 조회한다. */
        MeetingAttendeesResult result = service.getMeetingAttendees(
                new GetMeetingAttendeesQuery(10L, 7L, "MEMBER", false, 91L)
        );

        /* 개설자 3번이 첫 번째이고 나머지 참석자는 조회 순서를 유지해야 한다. */
        assertThat(result.meetingId()).isEqualTo(91L);
        assertThat(result.attendees())
                .extracting(MeetingAttendeesResult.Attendee::memberId)
                .containsExactly(3L, 7L, 11L);

        /* 개설자에게만 host 표시가 설정돼야 한다. */
        assertThat(result.attendees())
                .extracting(MeetingAttendeesResult.Attendee::host)
                .containsExactly(true, false, false);
    }

    /* 회사 OWNER가 참석자가 아니어도 회사 내 회의 명단을 읽을 수 있는지 검증한다. */
    @Test
    @DisplayName("OWNER는 참석자가 아니어도 회사 내 회의 명단을 조회한다")
    void ownerReadsCompanyMeetingRoster() {
        /* 정상 회의와 구성원 정보를 반환하는 조회 서비스를 준비한다. */
        MeetingQueryService service = new MeetingQueryService(
                repository(Optional.of(meeting(List.of(3L, 7L, 11L)))),
                memberPort()
        );

        /* 참석자가 아닌 99번 OWNER가 같은 회사 회의를 조회한다. */
        MeetingAttendeesResult result = service.getMeetingAttendees(
                new GetMeetingAttendeesQuery(10L, 99L, "OWNER", false, 91L)
        );

        /* 권한이 있는 회사 관리자는 전체 명단을 정상적으로 받아야 한다. */
        assertThat(result.attendees()).hasSize(3);
    }

    /* 일반 비참석자가 회의 존재를 알아도 명단을 읽을 수 없는지 검증한다. */
    @Test
    @DisplayName("일반 비참석자는 MT-011로 거절한다")
    void rejectsNonAttendee() {
        /* 회의는 존재하지만 요청자가 참석자가 아닌 상황을 준비한다. */
        MeetingQueryService service = new MeetingQueryService(
                repository(Optional.of(meeting(List.of(3L, 7L, 11L)))),
                memberPort()
        );

        /* 권한 없는 요청은 구성원 조회 전에 MT-011로 끝나야 한다. */
        assertErrorCode(
                () -> service.getMeetingAttendees(
                        new GetMeetingAttendeesQuery(10L, 99L, "MEMBER", false, 91L)
                ),
                "MT-011"
        );
    }

    /* 타 회사 또는 존재하지 않는 회의가 MT-001로 숨겨지는지 검증한다. */
    @Test
    @DisplayName("회사 범위에서 회의를 찾지 못하면 MT-001로 처리한다")
    void hidesMissingOrOtherCompanyMeeting() {
        /* 빈 결과를 반환하는 회사 범위 조회 저장소를 준비한다. */
        MeetingQueryService service = new MeetingQueryService(repository(Optional.empty()), memberPort());

        /* 회의가 없으면 열람 권한 판정 전에 MT-001이 발생해야 한다. */
        assertErrorCode(
                () -> service.getMeetingAttendees(
                        new GetMeetingAttendeesQuery(10L, 7L, "MEMBER", false, 91L)
                ),
                "MT-001"
        );
    }

    /* E finalize 조회가 D 회의 메타와 B 참석자 표시 정보를 함께 반환하는지 검증한다. */
    @Test
    @DisplayName("E 출처 회의 조회 결과에 회의 메타와 참석자 표시 정보를 제공한다")
    void returnsMeetingHistoryForHandoverSnapshot() {
        /* 정상 단건 회의를 반환하는 공통 조회 서비스를 준비한다. */
        MeetingQueryService service = new MeetingQueryService(
                repository(Optional.of(meeting(List.of(3L, 7L, 11L)))),
                memberPort()
        );

        /* E Adapter가 호출할 회사 범위 단건 조회 메서드를 실행한다. */
        var result = service.findMeeting(10L, 91L);

        /* D가 소유하는 제목·프로젝트·개설자 값이 반환돼야 한다. */
        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("A커머스 온보딩 킥오프");
        assertThat(result.get().projectId()).isEqualTo(12L);
        assertThat(result.get().hostMemberId()).isEqualTo(3L);

        /* E가 finalize 스냅샷에 저장할 참석자 이름과 팀도 개설자 우선으로 채워져야 한다. */
        assertThat(result.get().attendees())
                .extracting(attendee -> attendee.name())
                .containsExactly("지우", "이든", "하린");
    }

    /* E 공개 Port의 프로젝트 회의·안건·참석자 배치 결과가 D 소유 DTO로 변환되는지 검증한다. */
    @Test
    @DisplayName("MeetingQueryPort는 프로젝트 회의 맥락을 배치 조회한다")
    void returnsProjectMeetingContextThroughPublicPort() {
        /* 단건과 배치 조회 값을 모두 제공하는 D 조회 서비스를 준비한다. */
        MeetingQueryService service = new MeetingQueryService(
                repository(Optional.of(meeting(List.of(3L, 7L, 11L)))),
                memberPort()
        );

        /* E가 finalize 시점에 호출할 세 가지 프로젝트 회의 조회를 실행한다. */
        var meetings = service.findProjectMeetingsOrdered(10L, 12L);
        var topics = service.findMeetingTopics(10L, List.of(91L));
        var attendees = service.findMeetingAttendees(10L, List.of(91L));

        /* 회의 타임라인에 필요한 제목과 개설자가 D 결과 DTO로 반환돼야 한다. */
        assertThat(meetings).singleElement().satisfies(result -> {
            /* 프로젝트 타임라인의 핵심 식별자와 개설자 값을 확인한다. */
            assertThat(result.meetingId()).isEqualTo(91L);
            assertThat(result.title()).isEqualTo("A커머스 온보딩 킥오프");
            assertThat(result.hostMemberId()).isEqualTo(3L);
        });

        /* 안건 유형과 표시 순서가 손실 없이 공개 Port 결과로 변환돼야 한다. */
        assertThat(topics).singleElement().satisfies(result -> {
            /* E가 회의 맥락을 조립할 MAIN 유형과 내용을 확인한다. */
            assertThat(result.type()).isEqualTo(MeetingTopicType.MAIN);
            assertThat(result.content()).isEqualTo("출시 범위 확정");
            assertThat(result.sortOrder()).isZero();
        });

        /* 참석자 배치 결과는 회의와 구성원 식별자 쌍을 모두 보존해야 한다. */
        assertThat(attendees)
                .extracting(result -> result.memberId())
                .containsExactly(3L, 7L, 11L);
    }

    /* 테스트에서 지정한 단건 회의를 반환하는 조회 저장소 대역을 만든다. */
    private MeetingQueryRepository repository(Optional<MeetingSnapshot> meeting) {
        /* RESULT-01 외의 E 배치 메서드는 이 단위 테스트에서 빈 결과를 반환한다. */
        return new MeetingQueryRepository() {
            /* 회사 범위 회의 단건 결과를 그대로 반환한다. */
            @Override
            public Optional<MeetingSnapshot> findMeeting(Long companyId, Long meetingId) {
                /* 서비스 검증에 사용할 준비된 Optional을 반환한다. */
                return meeting;
            }

            /* 프로젝트 타임라인 조회에 사용할 회의 한 건을 반환한다. */
            @Override
            public List<ProjectMeetingSnapshot> findProjectMeetingsOrdered(Long companyId, Long projectId) {
                /* D가 소유하는 회의 메타 필드로 E 타임라인용 조회 모델을 만든다. */
                return List.of(new ProjectMeetingSnapshot(
                        91L,
                        "A커머스 온보딩 킥오프",
                        LocalDateTime.of(2026, 8, 6, 14, 0),
                        3L,
                        MeetingStatus.SCHEDULED
                ));
            }

            /* MEET-03 조회는 RESULT-01 서비스 단위 테스트 대상이 아니므로 빈 목록을 반환한다. */
            @Override
            public List<UpcomingMeetingSnapshot> findUpcomingMeetings(
                    Long companyId,
                    Long memberId,
                    LocalDateTime now,
                    int limit
            ) {
                /* 별도 UpcomingMeetingQueryServiceTest에서 검증하므로 여기서는 호출되지 않는다. */
                return List.of();
            }

            /* 회의 맥락 타임라인 조회에 사용할 MAIN 안건 한 건을 반환한다. */
            @Override
            public List<MeetingTopicSnapshot> findMeetingTopics(Long companyId, List<Long> meetingIds) {
                /* 안건의 유형, 내용, 표시 순서를 가진 조회 모델을 반환한다. */
                return List.of(new MeetingTopicSnapshot(
                        91L,
                        MeetingTopicType.MAIN,
                        "출시 범위 확정",
                        0
                ));
            }

            /* 배치 참석자 조회에 사용할 회의와 구성원 식별자 쌍을 반환한다. */
            @Override
            public List<MeetingAttendeeReference> findMeetingAttendees(Long companyId, List<Long> meetingIds) {
                /* E가 회의별 참석자를 조립할 수 있도록 세 쌍을 반환한다. */
                return List.of(
                        new MeetingAttendeeReference(91L, 3L),
                        new MeetingAttendeeReference(91L, 7L),
                        new MeetingAttendeeReference(91L, 11L)
                );
            }
        };
    }

    /* 개설자와 두 참석자의 표시 정보를 반환하는 B도메인 포트 대역을 만든다. */
    private MemberQueryPort memberPort() {
        /* 요청 식별자에 대응하는 구성원 세 명을 일괄 반환한다. */
        return (companyId, memberIds) -> List.of(
                new MemberQueryPort.MemberSnapshot(3L, "지우", 100L, "기획"),
                new MemberQueryPort.MemberSnapshot(7L, "이든", 200L, "개발"),
                new MemberQueryPort.MemberSnapshot(11L, "하린", 300L, "디자인")
        );
    }

    /* 정상 필드와 전달받은 참석자 식별자로 회의 조회 모델을 만든다. */
    private MeetingSnapshot meeting(List<Long> attendeeMemberIds) {
        /* RESULT-01 권한과 명단 변환에 필요한 실제 회의 필드를 채운다. */
        return new MeetingSnapshot(
                91L,
                10L,
                12L,
                3L,
                "A커머스 온보딩 킥오프",
                MeetingStatus.SCHEDULED,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                null,
                null,
                attendeeMemberIds
        );
    }

    /* 실행 결과가 예상 서비스 오류 코드인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* 예외 타입과 외부 계약 코드를 한 번에 확인한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
