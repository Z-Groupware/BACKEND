package com.module06.backend.meeting.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.CancelMeetingCommand;
import com.module06.backend.meeting.application.usecase.CancelMeetingUseCase;

/* MEET-06 시작 전 회의 취소와 예약 슬롯 해제를 제공하는 REST Controller다. */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingCancellationController {

    /* 프레젠테이션 계층과 회의 취소 애플리케이션 서비스 사이의 인바운드 Port다. */
    private final CancelMeetingUseCase cancelMeetingUseCase;

    /* 시작 전 회의를 취소하되 회의와 참석자 이력은 보존하고 예약 슬롯만 해제한다. */
    @Operation(
            summary = "회의 취소",
            description = "시작 전 회의를 취소하고 회의실 예약 슬롯을 해제합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @DeleteMapping("/{meetingId}")
    public ApiResponse<Void> cancelMeeting(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal me,
            @Parameter(description = "취소할 회의 식별자", required = true, example = "91")
            @PathVariable Long meetingId
    ) {
        /* 조작 가능한 요청값 대신 인증 principal과 Path만 사용해 취소 유스케이스를 실행한다. */
        cancelMeetingUseCase.cancelMeeting(new CancelMeetingCommand(
                me.getCompanyId(),
                me.getMemberId(),
                me.getAuthority(),
                me.isAdmin(),
                meetingId
        ));

        /* 최초 취소와 재취소 모두 같은 명세의 data 없는 200 성공 응답을 반환한다. */
        return ApiResponse.successWithoutData("회의를 취소했습니다.");
    }
}
