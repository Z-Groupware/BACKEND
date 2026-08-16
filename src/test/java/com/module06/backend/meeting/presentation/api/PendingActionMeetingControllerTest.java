package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.query.GetPendingActionMeetingsQuery;
import com.module06.backend.meeting.application.result.PendingActionMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetPendingActionMeetingsUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.presentation.api.response.PendingActionMeetingListResponse;

/*
 * MEET-10 Controller가 인증 식별자를 Query에 넣고 외부 응답 계약으로 변환하는지 검증한다.
 */
@DisplayName("MEET-10 확정 대기 회의 Controller")
class PendingActionMeetingControllerTest {

    /* 인증 principal이 Query에 전달되고 명세 응답이 만들어지는지 검증한다. */
    @Test
    @DisplayName("확정 대기 회의 목록을 200 공통 응답으로 반환한다")
    void returnsPendingActionMeetingList() {
        /* 유스케이스에 전달된 Query를 기록할 공간을 준비한다. */
        GetPendingActionMeetingsQuery[] capturedQuery = new GetPendingActionMeetingsQuery[1];

        /* Query를 기록하고 확정 대기 회의 한 건을 반환하는 유스케이스 대역을 만든다. */
        GetPendingActionMeetingsUseCase useCase = query -> {
            capturedQuery[0] = query;
            return result();
        };
        PendingActionMeetingController controller = new PendingActionMeetingController(useCase);

        /* 인증 principal 값으로 Controller 메서드를 호출한다. */
        ApiResponse<PendingActionMeetingListResponse> response =
                controller.getPendingActionMeetings(10L, 3L);

        /* 회사·구성원 식별자는 요청값이 아닌 인증 principal 값으로 Query에 전달돼야 한다. */
        assertThat(capturedQuery[0].companyId()).isEqualTo(10L);
        assertThat(capturedQuery[0].requesterMemberId()).isEqualTo(3L);

        /* 공통 200 상태와 MEET-10 성공 메시지가 응답에 포함돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("확정 대기 회의 목록을 조회했습니다.");

        /* 일시는 초 단위 문자열이고 대기 건수·프로젝트 값이 명세 필드로 변환돼야 한다. */
        var meeting = response.getData().meetings().get(0);
        assertThat(meeting.meetingId()).isEqualTo(13L);
        assertThat(meeting.status()).isEqualTo("DONE");
        assertThat(meeting.startAt()).isEqualTo("2026-08-07T14:00:00");
        assertThat(meeting.pendingActionCount()).isEqualTo(3L);
        assertThat(meeting.project().tag()).isEqualTo("Z-GROUPWARE");
    }

    /* 확정 대기 회의가 없을 때 빈 배열이 정상 응답으로 나가는지 검증한다. */
    @Test
    @DisplayName("확정 대기 회의가 없으면 빈 배열을 200으로 반환한다")
    void returnsEmptyArrayWhenNoPendingMeeting() {
        /* 빈 결과를 반환하는 유스케이스 대역으로 Controller를 구성한다. */
        GetPendingActionMeetingsUseCase useCase = query -> new PendingActionMeetingListResult(List.of());
        PendingActionMeetingController controller = new PendingActionMeetingController(useCase);

        /* 인증 principal 값으로 Controller 메서드를 호출한다. */
        ApiResponse<PendingActionMeetingListResponse> response =
                controller.getPendingActionMeetings(10L, 3L);

        /* 404가 아니라 200과 빈 배열이어야 하며 meetings가 null이면 안 된다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData().meetings()).isNotNull().isEmpty();
    }

    /* 예정 일시가 없는 완료 비대면 회의도 목록 응답 변환에 실패하지 않는지 검증한다. */
    @Test
    @DisplayName("비대면 회의의 시작 시각이 없으면 null로 반환한다")
    void returnsNullStartAtForOnlineMeeting() {
        /* 시작 시각이 없는 비대면 회의 결과를 반환하는 유스케이스 대역을 만든다. */
        GetPendingActionMeetingsUseCase useCase = query -> new PendingActionMeetingListResult(List.of(
                new PendingActionMeetingListResult.MeetingItem(
                        14L,
                        "비대면 녹음 회의",
                        MeetingStatus.DONE,
                        null,
                        2L,
                        new PendingActionMeetingListResult.Project(12L, "Z-GROUPWARE", "잇다 그룹웨어")
                )
        ));
        PendingActionMeetingController controller = new PendingActionMeetingController(useCase);

        /* 인증 principal 값으로 비대면 확정 대기 회의 목록을 조회한다. */
        ApiResponse<PendingActionMeetingListResponse> response =
                controller.getPendingActionMeetings(10L, 3L);

        /* 응답 변환은 500으로 실패하지 않고 비대면 회의의 선택 시각을 null로 유지해야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData().meetings()).hasSize(1);
        assertThat(response.getData().meetings().get(0).startAt()).isNull();
    }

    /* Controller 응답 변환에 사용할 확정 대기 회의 결과 한 건을 만든다. */
    private PendingActionMeetingListResult result() {
        /* 명세 예시와 동일한 회의·프로젝트 값으로 애플리케이션 결과를 구성한다. */
        return new PendingActionMeetingListResult(List.of(
                new PendingActionMeetingListResult.MeetingItem(
                        13L,
                        "주간 백엔드 회의",
                        MeetingStatus.DONE,
                        LocalDateTime.of(2026, 8, 7, 14, 0),
                        3L,
                        new PendingActionMeetingListResult.Project(12L, "Z-GROUPWARE", "잇다 그룹웨어")
                )
        ));
    }
}
