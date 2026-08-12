package com.module06.backend.meeting.presentation.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.module06.backend.meeting.application.query.GetMeetingAttendeesQuery;
import com.module06.backend.meeting.application.result.MeetingAttendeeUpdateResult;
import com.module06.backend.meeting.application.result.MeetingAttendeesResult;
import com.module06.backend.meeting.application.usecase.GetMeetingAttendeesUseCase;
import com.module06.backend.meeting.application.usecase.ReplaceMeetingAttendeesUseCase;
import com.module06.backend.meeting.presentation.api.request.ReplaceMeetingAttendeesRequest;
import com.module06.backend.meeting.presentation.api.response.MeetingAttendeeListResponse;
import com.module06.backend.meeting.presentation.api.response.MeetingAttendeeUpdateResponse;

/*
 * 회의 참석자 명단 REST API의 진입점이다.
 *
 * RESULT-01의 GET과 MEET-09의 PUT이 같은 참석자 리소스를 공유하므로
 * 핵심 회의 Controller와 분리해 참석자 조회·교체 기능을 한 곳에 모은다.
 */
@Tag(name = "Meeting Attendee", description = "회의 참석자 조회 및 관리 API")
@RestController
@RequestMapping("/api/meetings/{meetingId}/attendees")
@RequiredArgsConstructor
public class MeetingAttendeeController {

    /* RESULT-01 Controller와 회의 조회 서비스 사이의 인바운드 포트다. */
    private final GetMeetingAttendeesUseCase getMeetingAttendeesUseCase;

    /* MEET-09 Controller와 참석자 교체 서비스 사이의 인바운드 포트다. */
    private final ReplaceMeetingAttendeesUseCase replaceMeetingAttendeesUseCase;

    /*
     * 열람 권한이 있는 회의의 개설자 포함 전체 참석자 명단을 조회한다.
     *
     * @param companyId 인증 principal에서 추출한 회사 식별자
     * @param memberId 인증 principal에서 추출한 요청자 구성원 식별자
     * @param role 인증 principal에서 추출한 기본 역할
     * @param isAdmin 인증 principal에서 추출한 관리자 권한 여부
     * @param meetingId Path에서 전달된 대상 회의 식별자
     * @return A 화자 판정 키를 포함한 회의 참석자 명단
     */
    @Operation(
            summary = "회의 참석자 조회",
            description = "열람 권한이 있는 회의의 개설자 포함 참석자 명단을 STT 화자 후보 형식으로 조회합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping
    public ApiResponse<MeetingAttendeeListResponse> getMeetingAttendees(
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
            @PathVariable Long meetingId
    ) {
        /* 인증 정보와 Path 식별자를 조회 조건으로 묶어 RESULT-01 유스케이스를 실행한다. */
        MeetingAttendeesResult result = getMeetingAttendeesUseCase.getMeetingAttendees(
                new GetMeetingAttendeesQuery(
                        companyId,
                        memberId,
                        role,
                        isAdmin,
                        meetingId
                )
        );

        /* A 연동 형식으로 변환한 참석자 명단을 공통 200 성공 응답으로 반환한다. */
        return ApiResponse.success(
                "회의 참석자 조회에 성공했습니다.",
                MeetingAttendeeListResponse.from(result)
        );
    }

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
