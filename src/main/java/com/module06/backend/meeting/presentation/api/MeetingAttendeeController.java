package com.module06.backend.meeting.presentation.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.result.MeetingAttendeeUpdateResult;
import com.module06.backend.meeting.application.usecase.ReplaceMeetingAttendeesUseCase;
import com.module06.backend.meeting.presentation.api.request.ReplaceMeetingAttendeesRequest;
import com.module06.backend.meeting.presentation.api.response.MeetingAttendeeUpdateResponse;

/*
 * MEET-09 참석자 명단 교체 REST API의 진입점이다.
 *
 * RESULT-01(참석자 조회 GET)은 FE·타 도메인 호출처가 없어 2026-08-13 제거했다 —
 * 이 리소스에는 이제 PUT만 남는다.
 */
@Tag(name = "Meeting Attendee", description = "회의 참석자 관리 API")
@RestController
@RequestMapping("/api/meetings/{meetingId}/attendees")
@RequiredArgsConstructor
public class MeetingAttendeeController {

    /* MEET-09 Controller와 참석자 교체 서비스 사이의 인바운드 포트다. */
    private final ReplaceMeetingAttendeesUseCase replaceMeetingAttendeesUseCase;

    /* host·OWNER·ADMIN 권한으로 회의 참석자 명단을 전체 교체한다. */
    @Operation(
            summary = "회의 참석자 명단 교체",
            description = "회의의 입장 허용 및 STT 화자 후보 명단을 전체 교체하며 개설자는 자동 포함합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PutMapping
    public ApiResponse<MeetingAttendeeUpdateResponse> replaceMeetingAttendees(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Parameter(hidden = true)
            // AuthPrincipal 의 필드명이 role → authority 로 바뀌었다(V2.3.1). SpEL 은 문자열이라
            // 컴파일에 걸리지 않고 런타임에 터지므로 여기도 함께 고친다. 담는 값은 그대로다.
            @AuthenticationPrincipal(expression = "authority") String role,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "isAdmin") boolean isAdmin,
            @Parameter(description = "대상 회의 식별자", required = true, example = "91")
            @PathVariable Long meetingId,
            @Valid @RequestBody ReplaceMeetingAttendeesRequest request
    ) {
        /* 인증 정보와 Path 및 요청 본문을 Command로 결합해 MEET-09 유스케이스를 실행한다. */
        MeetingAttendeeUpdateResult result = replaceMeetingAttendeesUseCase.replaceMeetingAttendees(
                request.toCommand(companyId, memberId, role, isAdmin, meetingId)
        );

        /* 교체된 최종 참석자 명단을 공통 200 성공 응답으로 반환한다. */
        return ApiResponse.success(
                "참석자 명단을 변경했습니다.",
                MeetingAttendeeUpdateResponse.from(result)
        );
    }
}
