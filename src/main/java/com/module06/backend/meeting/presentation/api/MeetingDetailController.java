package com.module06.backend.meeting.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.query.GetMeetingDetailQuery;
import com.module06.backend.meeting.application.result.MeetingDetailResult;
import com.module06.backend.meeting.application.usecase.GetMeetingDetailUseCase;
import com.module06.backend.meeting.presentation.api.response.MeetingDetailResponse;

/*
 * MEET-04 회의 상세 조회 REST API의 진입점이다.
 *
 * 회사·요청자·역할·팀 범위는 인증 principal에서만 읽고 Path에는 대상 회의 식별자만 받는다.
 */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingDetailController {

    /* MEET-04 Controller와 상세 조회 서비스 사이의 인바운드 Port다. */
    private final GetMeetingDetailUseCase getMeetingDetailUseCase;

    /* 인증 사용자의 열람 범위 안에서 회의 메타와 참석자 명단을 상세 조회한다. */
    @Operation(
            summary = "회의 상세 조회",
            description = "열람 권한이 있는 회의의 메타 정보와 참석자 명단을 조회합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingDetailResponse> getMeetingDetail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "대상 회의 식별자", required = true, example = "91")
            @PathVariable Long meetingId
    ) {
        /* 조작할 수 없는 principal 인증 범위와 Path 식별자를 MEET-04 Query로 묶는다. */
        MeetingDetailResult result = getMeetingDetailUseCase.getMeetingDetail(
                new GetMeetingDetailQuery(
                        principal.getCompanyId(),
                        principal.getMemberId(),
                        principal.getTeamId(),
                        principal.getAuthority(),
                        principal.isAdmin(),
                        meetingId
                )
        );

        /* 애플리케이션 결과를 명세의 중첩 응답 형식으로 변환해 공통 200 응답으로 반환한다. */
        return ApiResponse.success(
                "회의 상세 조회에 성공했습니다.",
                MeetingDetailResponse.from(result)
        );
    }
}
