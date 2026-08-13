package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.query.GetMeetingListQuery;
import com.module06.backend.meeting.application.result.MeetingListResult;
import com.module06.backend.meeting.application.usecase.GetMeetingListUseCase;
import com.module06.backend.meeting.domain.model.MeetingListScope;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingSummaryStatus;
import com.module06.backend.meeting.presentation.api.response.MeetingListResponse;

/*
 * MEET-02 Controller의 인증 범위·필터 파싱·응답 변환 계약을 검증한다.
 */
@DisplayName("MEET-02 회의 목록 Controller")
class MeetingListControllerTest {

    /* OWNER principal과 모든 필터가 Query로 전달되고 200 응답으로 변환되는지 검증한다. */
    @Test
    @DisplayName("OWNER의 회의 목록 필터를 회사 전체 열람 Query로 전달한다")
    void returnsFilteredMeetingListForOwner() {
        /* 유스케이스에 전달된 Query를 기록할 공간을 준비한다. */
        GetMeetingListQuery[] capturedQuery = new GetMeetingListQuery[1];

        /* Query를 기록하고 회의 한 건과 페이지 메타를 반환하는 유스케이스 대역을 만든다. */
        GetMeetingListUseCase useCase = query -> {
            capturedQuery[0] = query;
            return result();
        };
        MeetingListController controller = new MeetingListController(useCase);

        /* 회사 10의 OWNER principal과 명세의 전체 필터로 Controller 메서드를 호출한다. */
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "OWNER", false, null);
        ApiResponse<MeetingListResponse> response = controller.getMeetings(
                principal,
                12L,
                2L,
                "2026-08-01",
                "2026-08-07",
                "DONE",
                "HOSTED",
                "1",
                "20"
        );

        /* 인증 식별자와 OWNER 회사 전체 열람 여부가 요청값과 분리되어 전달돼야 한다. */
        assertThat(capturedQuery[0].companyId()).isEqualTo(10L);
        assertThat(capturedQuery[0].requesterMemberId()).isEqualTo(3L);
        assertThat(capturedQuery[0].companyWideRead()).isTrue();

        /* 날짜·상태·scope·페이지 문자열은 정확한 타입으로 변환돼야 한다. */
        assertThat(capturedQuery[0].from()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(capturedQuery[0].to()).isEqualTo(LocalDate.of(2026, 8, 7));
        assertThat(capturedQuery[0].status()).isEqualTo(MeetingStatus.DONE);
        assertThat(capturedQuery[0].scope()).isEqualTo(MeetingListScope.HOSTED);
        assertThat(capturedQuery[0].page()).isEqualTo(1);
        assertThat(capturedQuery[0].size()).isEqualTo(20);

        /* 공통 상태와 성공 메시지 및 페이지 메타가 명세대로 반환돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의 목록 조회에 성공했습니다.");
        assertThat(response.getData().page().totalElements()).isEqualTo(37L);

        /* 회의 일시와 중첩 회의실·프로젝트·참석자 표시값이 외부 응답 형식으로 변환돼야 한다. */
        var meeting = response.getData().meetings().get(0);
        assertThat(meeting.startAt()).isEqualTo("2026-08-04T14:00:00");
        assertThat(meeting.actionCount()).isEqualTo(5L);
        assertThat(meeting.teamId()).isEqualTo(100L);
        assertThat(meeting.originLabel()).isEqualTo("TEAM");
        assertThat(meeting.summaryStatus()).isEqualTo("STALLED");
        assertThat(meeting.agendaPreview().mainTopic()).isEqualTo("Main agenda");
        assertThat(meeting.isHost()).isTrue();
        assertThat(meeting.entryAvailable()).isFalse();
        assertThat(meeting.durationMinutes()).isEqualTo(60);
        assertThat(meeting.attendees())
                .extracting(MeetingListResponse.AttendeeResponse::memberId)
                .containsExactly(3L, 7L);
        assertThat(meeting.meetingRoom().name()).isEqualTo("회의실 B");
        assertThat(meeting.project().tag()).isEqualTo("acommerce");
    }

    /* 일반 구성원에게 회사 전체 열람 플래그가 부여되지 않는지 검증한다. */
    @Test
    @DisplayName("MEMBER는 참여한 회의만 조회하도록 제한 Query를 만든다")
    void restrictsMemberReadScope() {
        /* Query의 열람 범위만 기록하고 빈 페이지를 반환하는 유스케이스를 만든다. */
        GetMeetingListQuery[] capturedQuery = new GetMeetingListQuery[1];
        GetMeetingListUseCase useCase = query -> {
            capturedQuery[0] = query;
            return new MeetingListResult(List.of(), new MeetingListResult.Page(0, 20, 0L, 0));
        };
        MeetingListController controller = new MeetingListController(useCase);

        /* MEMBER principal로 필터 없는 기본 목록 조회를 호출한다. */
        controller.getMeetings(
                new AuthPrincipal(7L, 10L, "MEMBER", false, 100L),
                null,
                null,
                null,
                null,
                null,
                null,
                "0",
                "20"
        );

        /* 일반 구성원은 회사 전체가 아닌 개설·참석 회의 범위로 전달돼야 한다. */
        assertThat(capturedQuery[0].companyWideRead()).isFalse();
    }

    /* 잘못된 날짜·상태·페이지 문자열이 유스케이스 전에 입력 오류가 되는지 검증한다. */
    @Test
    @DisplayName("잘못된 필터 문자열은 IllegalArgumentException으로 거절한다")
    void rejectsMalformedFilterStrings() {
        /* 호출되면 실패하는 유스케이스로 Controller 선행 파싱을 검증한다. */
        GetMeetingListUseCase useCase = query -> {
            throw new AssertionError("잘못된 필터는 유스케이스까지 전달되면 안 됩니다.");
        };
        MeetingListController controller = new MeetingListController(useCase);
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "OWNER", false, null);

        /* ISO 날짜가 아닌 from은 명확한 입력 오류 메시지와 함께 거절돼야 한다. */
        assertThatThrownBy(() -> controller.getMeetings(
                principal, null, null, "2026/08/01", null, null, null, "0", "20"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from은 yyyy-MM-dd 형식이어야 합니다.");

        /* 정의되지 않은 상태와 scope, 정수가 아닌 페이지도 같은 입력 오류 계열이어야 한다. */
        assertThatThrownBy(() -> controller.getMeetings(
                principal, null, null, null, null, "CANCELLED", null, "0", "20"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status 값이 올바르지 않습니다.");
        assertThatThrownBy(() -> controller.getMeetings(
                principal, null, null, null, null, null, "TEAM", "0", "20"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scope 값이 올바르지 않습니다.");
        assertThatThrownBy(() -> controller.getMeetings(
                principal, null, null, null, null, null, null, "first", "20"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page은 정수여야 합니다.");
    }

    /* Controller 응답 변환에 사용할 완성된 회의 목록 결과를 만든다. */
    private MeetingListResult result() {
        /* API 명세 예시와 같은 회의·회의실·프로젝트·참석자 및 페이지 값을 반환한다. */
        return new MeetingListResult(
                List.of(new MeetingListResult.MeetingItem(
                        91L,
                        "A커머스 온보딩 킥오프",
                        MeetingStatus.DONE,
                        100L,
                        "TEAM",
                        MeetingSummaryStatus.STALLED,
                        LocalDateTime.of(2026, 8, 4, 14, 0),
                        LocalDateTime.of(2026, 8, 4, 15, 0),
                        4,
                        5L,
                        true,
                        false,
                        60,
                        List.of(
                                new MeetingListResult.Attendee(3L, "지우"),
                                new MeetingListResult.Attendee(7L, "이든")
                        ),
                        new MeetingListResult.AgendaPreview("Main agenda", "First sub agenda"),
                        new MeetingListResult.MeetingRoom(2L, "회의실 B"),
                        new MeetingListResult.Project(12L, "acommerce", "A커머스 온보딩")
                )),
                new MeetingListResult.Page(1, 20, 37L, 2)
        );
    }
}
