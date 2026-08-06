package com.module06.backend.meeting.presentation.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.StartCaptureSessionCommand;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult;
import com.module06.backend.meeting.application.usecase.StartCaptureSessionUseCase;
import com.module06.backend.meeting.presentation.api.response.CaptureSessionStartResponse;

/*
 * D 도메인이 소유하는 캡처 세션 생명주기 REST API의 진입점이다.
 *
 * A 도메인의 presign·청크·STT API와 분리해 세션 식별자와 상태 원본만 제공한다.
 */
@Tag(name = "Capture Session", description = "회의 캡처 세션 생명주기 API")
@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/capture-session")
@RequiredArgsConstructor
public class CaptureSessionController {

    /* CAP-01 프레젠테이션 계층과 캡처 세션 시작 서비스 사이의 인바운드 Port다. */
    private final StartCaptureSessionUseCase startCaptureSessionUseCase;

    /*
     * 진행 중인 회의의 host 요청으로 회의당 하나의 캡처 세션을 시작한다.
     * 역할 종류와 별개로 실제 host 여부는 서비스에서 meeting.host_member_id로 검증한다.
     */
    @Operation(
            summary = "캡처 세션 시작",
            description = "진행 중인 회의의 개설자가 ACTIVE 캡처 세션을 시작합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<CaptureSessionStartResponse> startCaptureSession(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "캡처를 시작할 회의 식별자", required = true)
            @PathVariable Long meetingId
    ) {
        /* 토큰의 회사·구성원 식별자와 Path 회의 식별자만 신뢰해 시작 명령을 만든다. */
        StartCaptureSessionCommand command = new StartCaptureSessionCommand(
                principal.getCompanyId(),
                principal.getMemberId(),
                meetingId
        );

        /* 캡처 세션 생성 결과를 명세의 201 공통 응답 형식으로 변환한다. */
        CaptureSessionStartResult result = startCaptureSessionUseCase.startCaptureSession(command);
        return ApiResponse.created(
                "캡처 세션을 시작했습니다.",
                CaptureSessionStartResponse.from(result)
        );
    }
}
