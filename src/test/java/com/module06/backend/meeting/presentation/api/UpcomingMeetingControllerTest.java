package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.query.GetUpcomingMeetingsQuery;
import com.module06.backend.meeting.application.result.UpcomingMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetUpcomingMeetingsUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.presentation.api.response.UpcomingMeetingListResponse;

/*
 * MEET-03 Controller가 인증 식별자와 limit을 Query에 넣고 외부 응답 계약으로 변환하는지 검증한다.
 */
@DisplayName("MEET-03 내 예정 회의 Controller")
class UpcomingMeetingControllerTest {

    /* 인증 principal과 Query Parameter가 전달되고 명세 응답이 만들어지는지 검증한다. */
    @Test
    @DisplayName("내 예정 회의 목록을 200 공통 응답으로 반환한다")
    void returnsUpcomingMeetingList() {
        /* 유스케이스에 전달된 Query를 기록할 공간을 준비한다. */
        GetUpcomingMeetingsQuery[] capturedQuery = new GetUpcomingMeetingsQuery[1];

        /* Query를 기록하고 예정 회의 한 건을 반환하는 유스케이스 대역을 만든다. */
        GetUpcomingMeetingsUseCase useCase = query -> {
            capturedQuery[0] = query;
            return result();
        };
        UpcomingMeetingController controller = new UpcomingMeetingController(useCase);

        /* 인증 principal 값과 요청 limit 7로 Controller 메서드를 호출한다. */
        ApiResponse<UpcomingMeetingListResponse> response = controller.getUpcomingMeetings(10L, 3L, "7");

        /* 회사·구성원 식별자는 요청값이 아닌 인증 principal 값으로 Query에 전달돼야 한다. */
        assertThat(capturedQuery[0].companyId()).isEqualTo(10L);
        assertThat(capturedQuery[0].requesterMemberId()).isEqualTo(3L);
        assertThat(capturedQuery[0].limit()).isEqualTo(7);

        /* 공통 200 상태와 MEET-03 성공 메시지가 응답에 포함돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("예정 회의 목록 조회에 성공했습니다.");

        /* 일시는 초 단위 문자열이고 중첩 회의실·프로젝트 값은 명세 필드로 변환돼야 한다. */
        var meeting = response.getData().meetings().get(0);
        assertThat(meeting.startAt()).isEqualTo("2026-08-05T09:05:00");
        assertThat(meeting.isHost()).isTrue();
        assertThat(meeting.entryAvailable()).isTrue();
        assertThat(meeting.meetingRoom().name()).isEqualTo("회의실 B");
        assertThat(meeting.project().tag()).isEqualTo("acommerce");
    }

    /* 숫자가 아닌 limit이 유스케이스 호출 전에 입력 오류로 변환되는지 검증한다. */
    @Test
    @DisplayName("숫자가 아닌 limit은 IllegalArgumentException으로 거절한다")
    void rejectsNonNumericLimit() {
        /* 호출되면 실패하는 유스케이스로 Controller의 선행 파싱을 검증한다. */
        GetUpcomingMeetingsUseCase useCase = query -> {
            throw new AssertionError("잘못된 limit은 유스케이스까지 전달되면 안 됩니다.");
        };
        UpcomingMeetingController controller = new UpcomingMeetingController(useCase);

        /* 숫자가 아닌 값은 공통 예외 처리기가 Z-001로 바꿀 IllegalArgumentException이어야 한다. */
        assertThatThrownBy(() -> controller.getUpcomingMeetings(10L, 3L, "five"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit은 정수여야 합니다.");
    }

    /* Controller 응답 변환에 사용할 완성된 예정 회의 결과를 만든다. */
    private UpcomingMeetingListResult result() {
        /* 명세 예시 구조와 동일한 회의·회의실·프로젝트 값을 반환한다. */
        return new UpcomingMeetingListResult(List.of(
                new UpcomingMeetingListResult.MeetingItem(
                        91L,
                        "A커머스 온보딩 킥오프",
                        MeetingStatus.SCHEDULED,
                        LocalDateTime.of(2026, 8, 5, 9, 5),
                        LocalDateTime.of(2026, 8, 5, 10, 5),
                        4,
                        true,
                        true,
                        new UpcomingMeetingListResult.MeetingRoom(2L, "회의실 B"),
                        new UpcomingMeetingListResult.Project(
                                12L,
                                "acommerce",
                                "A커머스 온보딩",
                                "#5B5BD6"
                        )
                )
        ));
    }
}
