package com.module06.backend.meeting.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.query.GetPendingActionMeetingsQuery;
import com.module06.backend.meeting.application.result.PendingActionMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetPendingActionMeetingsUseCase;
import com.module06.backend.meeting.presentation.api.response.PendingActionMeetingListResponse;

/*
 * MEET-10 확정 대기 회의 목록 REST API의 진입점이다.
 *
 * 다른 조회 API와 달리 OWNER·ADMIN이라도 남의 host 회의는 반환하지 않는다.
 * 이 화면은 "내가 처리할 일" 목록이라 롤이 아니라 host 본인 여부가 기준이며,
 * 인증 principal의 식별자만 사용해 다른 사용자의 목록을 요청할 수 없게 한다.
 */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class PendingActionMeetingController {

    /* MEET-10 프레젠테이션 계층과 조회 서비스 사이의 인바운드 Port다. */
    private final GetPendingActionMeetingsUseCase getPendingActionMeetingsUseCase;

    /* 로그인 사용자가 host인 종료 회의 중 액션 분배가 남은 회의를 최근 순으로 조회한다. */
    @Operation(
            summary = "확정 대기 회의 목록 조회",
            description = "로그인 사용자가 개설한 종료된 회의 중 아직 보드로 분배하지 않은 액션이 남은 회의를 조회합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/pending-action-distributions")
    public ApiResponse<PendingActionMeetingListResponse> getPendingActionMeetings(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        /* 인증 식별자만 Query로 묶어 MEET-10 유스케이스를 실행한다. */
        PendingActionMeetingListResult result = getPendingActionMeetingsUseCase.getPendingActionMeetings(
                new GetPendingActionMeetingsQuery(companyId, memberId)
        );

        /* 확정 대기 회의가 없는 경우에도 빈 배열을 포함하는 공통 200 성공 응답을 반환한다. */
        return ApiResponse.success(
                "확정 대기 회의 목록을 조회했습니다.",
                PendingActionMeetingListResponse.from(result)
        );
    }
}
