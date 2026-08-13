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
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.query.GetDashboardMeetingsQuery;
import com.module06.backend.meeting.application.result.DashboardMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetDashboardMeetingsUseCase;
import com.module06.backend.meeting.domain.model.DashboardMeetingScope;
import com.module06.backend.meeting.presentation.api.response.DashboardMeetingListResponse;

/*
 * MEET-17 대시보드 최근 회의 카드 조회 REST API의 진입점이다.
 *
 * companyId·memberId·teamId·역할은 전부 인증 principal에서만 읽고, 역할만으로 조회
 * 범위를 추론하지 않도록 scope는 요청자가 명시적으로 전달한다.
 */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class DashboardMeetingController {

    /* MEET-17 프레젠테이션 계층과 조회 서비스 사이의 인바운드 Port다. */
    private final GetDashboardMeetingsUseCase getDashboardMeetingsUseCase;

    /* 로그인 사용자의 scope 조건에 맞는 대시보드 최근 회의 카드를 조회한다. */
    @Operation(
            summary = "대시보드 최근 회의 조회",
            description = "Owner·팀장·사원 대시보드의 «최근 회의» 카드를 scope별로 조회합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardMeetingListResponse> getDashboardMeetings(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "조회 범위. owner · team · me", example = "me")
            @RequestParam(required = false) String scope,
            @Parameter(description = "최대 반환 개수. 기본 5, 최대 20", example = "5")
            @RequestParam(required = false) Integer limit
    ) {
        /* 인증 식별자와 파싱한 scope·limit을 Query로 묶어 MEET-17 유스케이스를 실행한다. */
        DashboardMeetingListResult result = getDashboardMeetingsUseCase.getDashboardMeetings(
                new GetDashboardMeetingsQuery(
                        principal.getCompanyId(),
                        principal.getMemberId(),
                        principal.getTeamId(),
                        principal.getAuthority(),
                        parseScope(scope),
                        limit
                )
        );

        /* 최근 회의가 없는 경우에도 빈 배열을 포함하는 공통 200 성공 응답을 반환한다. */
        return ApiResponse.success(
                "최근 대시보드 회의 조회에 성공했습니다.",
                DashboardMeetingListResponse.from(result)
        );
    }

    /* 필수 scope 문자열을 대시보드 조회 범위 enum으로 변환한다. */
    private DashboardMeetingScope parseScope(String value) {
        /* scope는 필수 파라미터이므로 생략·공백은 Z-001 처리 대상인 입력 오류로 변환한다. */
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("scope는 필수입니다.");
        }

        /* owner·team·me 소문자 값만 허용하고 그 외 값은 Z-001 처리 대상으로 변환한다. */
        try {
            return DashboardMeetingScope.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("scope 값이 올바르지 않습니다.", exception);
        }
    }
}
