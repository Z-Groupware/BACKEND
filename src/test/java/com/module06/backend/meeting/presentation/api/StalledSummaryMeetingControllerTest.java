package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.query.GetStalledSummaryMeetingsQuery;
import com.module06.backend.meeting.application.result.StalledSummaryMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetStalledSummaryMeetingsUseCase;
import com.module06.backend.meeting.presentation.api.response.StalledSummaryMeetingListResponse;

/* MEET-15 Controller가 인증·필터를 Query에 넣고 외부 응답으로 변환하는지 검증한다. */
@DisplayName("MEET-15 요약 중단 회의 Controller")
class StalledSummaryMeetingControllerTest {

    /* 인증 principal과 선택 필터가 Query에 전달되고 명세 응답이 만들어지는지 검증한다. */
    @Test
    @DisplayName("요약 중단·실패 회의 목록을 200 공통 응답으로 반환한다")
    void returnsStalledSummaryMeetingList() {
        /* 유스케이스에 전달된 Query를 기록할 공간을 준비한다. */
        GetStalledSummaryMeetingsQuery[] capturedQuery = new GetStalledSummaryMeetingsQuery[1];

        /* Query를 기록하고 중단·실패 회의 두 건을 반환하는 유스케이스 대역을 만든다. */
        GetStalledSummaryMeetingsUseCase useCase = query -> {
            capturedQuery[0] = query;
            return result();
        };
        StalledSummaryMeetingController controller = new StalledSummaryMeetingController(useCase);

        /* 회사 10번·구성원 3번 인증 principal과 명세 필터로 Controller를 호출한다. */
        ApiResponse<StalledSummaryMeetingListResponse> response = controller.getStalledSummaryMeetings(
                principal(),
                12L,
                "2026-08-01",
                "2026-08-31",
                "0",
                "20"
        );

        /* 회사·구성원 식별자는 조작 가능한 요청이 아니라 인증 principal에서 와야 한다. */
        assertThat(capturedQuery[0].companyId()).isEqualTo(10L);
        assertThat(capturedQuery[0].requesterMemberId()).isEqualTo(3L);

        /* 프로젝트·기간·페이지 문자열은 검증된 값으로 변환되어 Query에 전달돼야 한다. */
        assertThat(capturedQuery[0].projectId()).isEqualTo(12L);
        assertThat(capturedQuery[0].from()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(capturedQuery[0].to()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(capturedQuery[0].page()).isZero();
        assertThat(capturedQuery[0].size()).isEqualTo(20);

        /* 공통 200 상태와 MEET-15 성공 메시지가 응답에 포함돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("요약 중단 회의 목록을 조회했습니다.");

        /* true 중단과 false 실패가 화면 문구 분기에 사용할 필드로 변환돼야 한다. */
        assertThat(response.getData().meetings())
                .extracting(StalledSummaryMeetingListResponse.MeetingResponse::isStalled)
                .containsExactly(true, false);
        assertThat(response.getData().page().totalElements()).isEqualTo(2L);
    }

    /* 조회 결과가 없을 때 빈 배열과 페이지 메타가 정상 응답으로 나가는지 검증한다. */
    @Test
    @DisplayName("요약 문제 회의가 없으면 빈 배열을 200으로 반환한다")
    void returnsEmptyArrayWhenNoStalledSummaryMeeting() {
        /* 빈 결과를 반환하는 유스케이스 대역으로 Controller를 구성한다. */
        GetStalledSummaryMeetingsUseCase useCase = query -> new StalledSummaryMeetingListResult(
                List.of(),
                new StalledSummaryMeetingListResult.Page(0, 20, 0L, 0)
        );
        StalledSummaryMeetingController controller = new StalledSummaryMeetingController(useCase);

        /* 선택 필터를 모두 생략하고 기본 페이지 값으로 조회한다. */
        ApiResponse<StalledSummaryMeetingListResponse> response = controller.getStalledSummaryMeetings(
                principal(), null, null, null, "0", "20"
        );

        /* 404가 아니라 200과 null이 아닌 빈 배열·0건 페이지가 반환돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData().meetings()).isNotNull().isEmpty();
        assertThat(response.getData().page().totalElements()).isZero();
    }

    /* Controller 응답 변환에 사용할 요약 중단·실패 결과 두 건을 만든다. */
    private StalledSummaryMeetingListResult result() {
        /* 화면 예시와 동일한 중단·실패 회의를 최근 순으로 구성한다. */
        return new StalledSummaryMeetingListResult(
                List.of(
                        new StalledSummaryMeetingListResult.MeetingItem(
                                30L, "9월 스프린트 리뷰", true
                        ),
                        new StalledSummaryMeetingListResult.MeetingItem(
                                29L, "현업툴 리뉴얼 프로젝트 킥오프", false
                        )
                ),
                new StalledSummaryMeetingListResult.Page(0, 20, 2L, 1)
        );
    }

    /* 테스트에서 사용할 인증 principal을 만든다. */
    private AuthPrincipal principal() {
        /* 회사 10번의 MEMBER 구성원 3번으로 인증된 사용자를 반환한다. */
        return new AuthPrincipal(3L, 10L, "MEMBER", false, null);
    }
}
