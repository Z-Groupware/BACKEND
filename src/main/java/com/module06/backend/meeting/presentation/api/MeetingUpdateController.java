package com.module06.backend.meeting.presentation.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.result.MeetingUpdateResult;
import com.module06.backend.meeting.application.usecase.UpdateMeetingUseCase;
import com.module06.backend.meeting.presentation.api.request.UpdateMeetingRequest;
import com.module06.backend.meeting.presentation.api.response.UpdateMeetingResponse;

/*
 * MEET-05 회의 정보 수정 REST API의 진입점이다.
 *
 * 테넌트·요청자·권한 값은 인증 principal에서만 읽고 서비스가 host·OWNER·ADMIN 범위를 최종 검증한다.
 */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingUpdateController {

    /* MEET-05 프레젠테이션 계층과 수정 서비스 사이의 인바운드 Port다. */
    private final UpdateMeetingUseCase updateMeetingUseCase;

    /* 예약 상태 회의의 전달된 필드만 수정하고 최종 예약 표시 정보를 반환한다. */
    @Operation(
            summary = "회의 정보 수정",
            description = "시작 전 회의의 제목·프로젝트·회의실·시간·녹음 동의를 부분 수정합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PatchMapping("/{meetingId}")
    public ApiResponse<UpdateMeetingResponse> updateMeeting(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal me,
            @Parameter(description = "수정할 회의 식별자", required = true, example = "91")
            @PathVariable Long meetingId,
            @Valid @RequestBody UpdateMeetingRequest request
    ) {
        /* 인증 principal과 Path 및 PATCH 존재 정보를 조합해 수정 유스케이스를 실행한다. */
        MeetingUpdateResult result = updateMeetingUseCase.updateMeeting(request.toCommand(
                me.getCompanyId(),
                me.getMemberId(),
                me.getAuthority(),
                me.isAdmin(),
                meetingId
        ));

        /* 수정 후 최종 예약 상태를 명세의 공통 200 성공 응답으로 반환한다. */
        return ApiResponse.success(
                "회의 정보를 수정했습니다.",
                UpdateMeetingResponse.from(result)
        );
    }
}
