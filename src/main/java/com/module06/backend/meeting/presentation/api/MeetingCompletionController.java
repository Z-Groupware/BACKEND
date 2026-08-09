package com.module06.backend.meeting.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.CompleteMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingCompletionResult;
import com.module06.backend.meeting.application.usecase.CompleteMeetingUseCase;
import com.module06.backend.meeting.presentation.api.response.MeetingCompletionResponse;

/* MEET-08 회의 종료와 비동기 분석 접수를 제공하는 REST Controller다. */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingCompletionController {

    /* 프레젠테이션 계층과 회의 종료 애플리케이션 서비스 사이의 인바운드 Port다. */
    private final CompleteMeetingUseCase completeMeetingUseCase;

    /* 진행 중 회의를 종료하고 백그라운드 분석을 요청한다. */
    @Operation(
            summary = "회의 종료",
            description = "회의와 캡처 세션을 종료하고 백그라운드 분석을 요청합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/{meetingId}/complete")
    public ApiResponse<MeetingCompletionResponse> completeMeeting(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal me,
            @Parameter(description = "종료할 회의 식별자", required = true, example = "91")
            @PathVariable Long meetingId
    ) {
        /* 인증 principal과 Path 값을 결합해 서비스 계층의 세부 host·관리자 권한 검사를 실행한다. */
        MeetingCompletionResult result = completeMeetingUseCase.completeMeeting(
                new CompleteMeetingCommand(
                        me.getCompanyId(),
                        me.getMemberId(),
                        me.getAuthority(),
                        me.isAdmin(),
                        meetingId
                )
        );

        /* 종료 저장과 비동기 분석 접수 상태를 명세의 200 공통 응답으로 반환한다. */
        return ApiResponse.success(
                "회의를 종료했습니다.",
                MeetingCompletionResponse.from(result)
        );
    }
}
