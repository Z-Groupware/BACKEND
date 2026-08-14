package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.query.GetDashboardMeetingsQuery;
import com.module06.backend.meeting.application.result.DashboardMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetDashboardMeetingsUseCase;
import com.module06.backend.meeting.domain.model.DashboardMeetingScope;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.presentation.api.response.DashboardMeetingListResponse;

/*
 * MEET-17 Controller의 principal 전달·scope 파싱·외부 응답 변환 계약을 검증한다.
 */
@DisplayName("MEET-17 대시보드 최근 회의 Controller")
class DashboardMeetingControllerTest {

    /* 인증 principal의 식별자·역할과 소문자 scope 파라미터가 정확히 Query로 전달되는지 검증한다. */
    @Test
    @DisplayName("인증 principal과 scope·limit을 Query로 전달하고 200 응답으로 변환한다")
    void returnsDashboardMeetingsForRequestedScope() {
        GetDashboardMeetingsQuery[] capturedQuery = new GetDashboardMeetingsQuery[1];
        GetDashboardMeetingsUseCase useCase = query -> {
            capturedQuery[0] = query;
            return result();
        };
        DashboardMeetingController controller = new DashboardMeetingController(useCase);

        ApiResponse<DashboardMeetingListResponse> response = controller.getDashboardMeetings(
                new AuthPrincipal(7L, 10L, "LEADER", false, 100L),
                "team",
                10
        );

        /* 소문자 scope는 TEAM enum으로, 인증 식별자와 역할은 그대로 Query에 전달돼야 한다. */
        assertThat(capturedQuery[0].companyId()).isEqualTo(10L);
        assertThat(capturedQuery[0].requesterMemberId()).isEqualTo(7L);
        assertThat(capturedQuery[0].requesterTeamId()).isEqualTo(100L);
        assertThat(capturedQuery[0].requesterRole()).isEqualTo("LEADER");
        assertThat(capturedQuery[0].scope()).isEqualTo(DashboardMeetingScope.TEAM);
        assertThat(capturedQuery[0].limit()).isEqualTo(10);

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("최근 대시보드 회의 조회에 성공했습니다.");
        assertThat(response.getData().meetings().get(0).meetingId()).isEqualTo(91L);
        assertThat(response.getData().meetings().get(0).scheduledAt()).isEqualTo("2026-08-11T10:00:00");
        assertThat(response.getData().meetings().get(0).originLabel()).isNull();
        assertThat(response.getData().meetings().get(0).hostLabel()).isNull();
    }

    /* scope 파라미터 생략은 Z-001로 이어지는 공통 입력 오류로 즉시 거절돼야 한다. */
    @Test
    @DisplayName("scope가 생략되면 IllegalArgumentException으로 거절한다")
    void rejectsMissingScope() {
        GetDashboardMeetingsUseCase useCase = query -> {
            throw new AssertionError("scope 파싱에 실패하면 유스케이스를 호출하면 안 됩니다.");
        };
        DashboardMeetingController controller = new DashboardMeetingController(useCase);

        assertThatThrownBy(() -> controller.getDashboardMeetings(
                new AuthPrincipal(7L, 10L, "LEADER", false, 100L),
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /* 허용되지 않은 scope 문자열은 Z-001로 이어지는 공통 입력 오류로 즉시 거절돼야 한다. */
    @Test
    @DisplayName("scope 값이 owner·team·me가 아니면 IllegalArgumentException으로 거절한다")
    void rejectsInvalidScopeValue() {
        GetDashboardMeetingsUseCase useCase = query -> {
            throw new AssertionError("scope 파싱에 실패하면 유스케이스를 호출하면 안 됩니다.");
        };
        DashboardMeetingController controller = new DashboardMeetingController(useCase);

        assertThatThrownBy(() -> controller.getDashboardMeetings(
                new AuthPrincipal(7L, 10L, "LEADER", false, 100L),
                "all",
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /* Controller 응답 변환에 사용할 대시보드 카드 한 건짜리 결과를 만든다. */
    private DashboardMeetingListResult result() {
        return new DashboardMeetingListResult(java.util.List.of(
                new DashboardMeetingListResult.MeetingItem(
                        91L,
                        "실시간 알림 아키텍처 논의",
                        "COLLAB",
                        MeetingStatus.SCHEDULED,
                        "회의실 B",
                        LocalDateTime.of(2026, 8, 11, 10, 0),
                        2,
                        null,
                        null
                )
        ));
    }
}
