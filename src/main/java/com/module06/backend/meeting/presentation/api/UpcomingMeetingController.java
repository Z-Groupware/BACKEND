package com.module06.backend.meeting.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.query.GetUpcomingMeetingsQuery;
import com.module06.backend.meeting.application.result.UpcomingMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetUpcomingMeetingsUseCase;
import com.module06.backend.meeting.presentation.api.response.UpcomingMeetingListResponse;

/*
 * MEET-03 내 예정 회의 목록 REST API의 진입점이다.
 *
 * 인증 principal의 회사와 구성원 식별자를 사용해 다른 사용자의 예정 회의를 요청할 수 없게 한다.
 */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class UpcomingMeetingController {

    /* MEET-03 프레젠테이션 계층과 조회 서비스 사이의 인바운드 Port다. */
    private final GetUpcomingMeetingsUseCase getUpcomingMeetingsUseCase;

    /* 로그인 사용자가 참석할 예정·진행 중 회의를 가까운 시작 시각 순으로 조회한다. */
    @Operation(
            summary = "내 예정 회의 목록 조회",
            description = "로그인 사용자가 참석자로 등록된 예정 및 진행 중 회의를 시작 시각 순으로 조회합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/upcoming")
    public ApiResponse<UpcomingMeetingListResponse> getUpcomingMeetings(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Parameter(description = "조회할 최대 회의 수", example = "5")
            @RequestParam(defaultValue = "5") String limit
    ) {
        /* 인증 식별자와 숫자로 변환한 limit을 Query로 묶어 MEET-03 유스케이스를 실행한다. */
        UpcomingMeetingListResult result = getUpcomingMeetingsUseCase.getUpcomingMeetings(
                new GetUpcomingMeetingsQuery(companyId, memberId, parseLimit(limit))
        );

        /* 회의가 없는 경우에도 빈 배열을 포함하는 공통 200 성공 응답을 반환한다. */
        return ApiResponse.success(
                "예정 회의 목록 조회에 성공했습니다.",
                UpcomingMeetingListResponse.from(result)
        );
    }

    /* Query Parameter 문자열을 서비스 범위 검증에 전달할 정수로 변환한다. */
    private Integer parseLimit(String limit) {
        /* 숫자가 아닌 값은 공통 IllegalArgumentException 처리기를 통해 Z-001로 응답된다. */
        try {
            return Integer.valueOf(limit);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("limit은 정수여야 합니다.", exception);
        }
    }
}
