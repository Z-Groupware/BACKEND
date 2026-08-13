package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.query.GetMeetingDetailQuery;
import com.module06.backend.meeting.application.result.MeetingDetailResult;
import com.module06.backend.meeting.application.usecase.GetMeetingDetailUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingSummaryStatus;
import com.module06.backend.meeting.presentation.api.response.MeetingDetailResponse;

/*
 * MEET-04 Controller의 principal 전달과 외부 응답 변환 계약을 검증한다.
 */
@DisplayName("MEET-04 회의 상세 Controller")
class MeetingDetailControllerTest {

    /* 인증 principal이 Query로 전달되고 nullable 일시가 명세 형식으로 변환되는지 검증한다. */
    @Test
    @DisplayName("인증 범위로 회의 상세를 조회하고 200 응답으로 변환한다")
    void returnsMeetingDetailForAuthenticatedPrincipal() {
        /* Controller가 만든 Query를 기록할 한 칸짜리 저장 공간을 준비한다. */
        GetMeetingDetailQuery[] capturedQuery = new GetMeetingDetailQuery[1];

        /* Query를 기록하고 예약 상태의 회의 상세 결과를 반환하는 유스케이스 대역을 만든다. */
        GetMeetingDetailUseCase useCase = query -> {
            capturedQuery[0] = query;
            return result();
        };
        MeetingDetailController controller = new MeetingDetailController(useCase);

        /* 회사·구성원·팀·역할이 담긴 LEADER principal로 91번 회의를 조회한다. */
        ApiResponse<MeetingDetailResponse> response = controller.getMeetingDetail(
                new AuthPrincipal(7L, 10L, "LEADER", false, 100L),
                91L
        );

        /* 조작 불가능한 인증 값과 Path 식별자가 Query에 정확히 전달돼야 한다. */
        assertThat(capturedQuery[0].companyId()).isEqualTo(10L);
        assertThat(capturedQuery[0].requesterMemberId()).isEqualTo(7L);
        assertThat(capturedQuery[0].requesterTeamId()).isEqualTo(100L);
        assertThat(capturedQuery[0].requesterRole()).isEqualTo("LEADER");
        assertThat(capturedQuery[0].meetingId()).isEqualTo(91L);

        /* 공통 성공 상태와 명세의 성공 메시지가 반환돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의 상세 조회에 성공했습니다.");

        /* 초 단위 고정 일시와 예약 상태의 nullable 실측 시각이 응답에 반영돼야 한다. */
        assertThat(response.getData().startAt()).isEqualTo("2026-08-04T14:00:00");
        assertThat(response.getData().startedAt()).isNull();
        assertThat(response.getData().endedAt()).isNull();

        /* 종료 전 회의는 액션·요약 신호가 기본값(0건·NONE)으로 응답에 반영돼야 한다. */
        assertThat(response.getData().pendingActionCount()).isZero();
        assertThat(response.getData().summaryStatus()).isEqualTo("NONE");

        /* 프로젝트·회의실·개설자·참석자 직급의 중첩 표시 정보가 유지돼야 한다. */
        assertThat(response.getData().project().color()).isEqualTo("#5B5BD6");
        assertThat(response.getData().meetingRoom().location()).isEqualTo("박애관 422호");
        assertThat(response.getData().host().name()).isEqualTo("지우");
        assertThat(response.getData().attendees().get(0).jobPosition()).isEqualTo("팀장");
    }

    /* Controller 응답 변환에 사용할 전체 중첩 표시 정보를 가진 예약 회의 결과를 만든다. */
    private MeetingDetailResult result() {
        /* 실제 시작·종료 전 상태를 검증하기 위해 startedAt과 endedAt은 null로 둔다. */
        return new MeetingDetailResult(
                91L,
                "A커머스 온보딩 킥오프",
                MeetingStatus.SCHEDULED,
                LocalDateTime.of(2026, 8, 4, 14, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0),
                null,
                null,
                true,
                0L,
                MeetingSummaryStatus.NONE,
                new MeetingDetailResult.Project(
                        12L,
                        "acommerce",
                        "A커머스 온보딩",
                        "#5B5BD6"
                ),
                new MeetingDetailResult.MeetingRoom(2L, "회의실 B", "박애관 422호"),
                new MeetingDetailResult.Host(3L, "지우"),
                List.of(new MeetingDetailResult.Attendee(3L, "지우", "기획", "팀장")),
                LocalDateTime.of(2026, 8, 1, 10, 12)
        );
    }
}
