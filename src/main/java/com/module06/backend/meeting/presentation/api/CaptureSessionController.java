package com.module06.backend.meeting.presentation.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.module06.backend.meeting.application.command.PauseCaptureSessionCommand;
import com.module06.backend.meeting.application.command.ResumeCaptureSessionCommand;
import com.module06.backend.meeting.application.command.StartCaptureSessionCommand;
import com.module06.backend.meeting.application.query.GetCaptureSessionQuery;
import com.module06.backend.meeting.application.result.CaptureSessionPauseResult;
import com.module06.backend.meeting.application.result.CaptureSessionResumeResult;
import com.module06.backend.meeting.application.result.CaptureSessionStateResult;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult;
import com.module06.backend.meeting.application.usecase.GetCaptureSessionUseCase;
import com.module06.backend.meeting.application.usecase.PauseCaptureSessionUseCase;
import com.module06.backend.meeting.application.usecase.ResumeCaptureSessionUseCase;
import com.module06.backend.meeting.application.usecase.StartCaptureSessionUseCase;
import com.module06.backend.meeting.presentation.api.response.CaptureSessionPauseResponse;
import com.module06.backend.meeting.presentation.api.response.CaptureSessionResumeResponse;
import com.module06.backend.meeting.presentation.api.response.CaptureSessionStateResponse;
import com.module06.backend.meeting.presentation.api.response.CaptureSessionStartResponse;

/*
 * D 도메인이 소유하는 캡처 세션 생명주기 REST API의 진입점이다.
 *
 * A 도메인의 presign·청크·STT API와 분리해 세션 식별자와 상태 원본만 제공한다.
 */
@Tag(name = "Capture Session", description = "회의 캡처 세션 생명주기 API")
@RestController
@RequestMapping("/api/meetings/{meetingId}/capture-session")
@RequiredArgsConstructor
public class CaptureSessionController {

    /* CAP-01 프레젠테이션 계층과 캡처 세션 시작 서비스 사이의 인바운드 Port다. */
    private final StartCaptureSessionUseCase startCaptureSessionUseCase;

    /* CAP-02 프레젠테이션 계층과 캡처 일시정지 서비스 사이의 인바운드 Port다. */
    private final PauseCaptureSessionUseCase pauseCaptureSessionUseCase;

    /* CAP-03 프레젠테이션 계층과 캡처 재개 서비스 사이의 인바운드 Port다. */
    private final ResumeCaptureSessionUseCase resumeCaptureSessionUseCase;

    /* CAP-10 프레젠테이션 계층과 현재 캡처 세션 조회 서비스 사이의 인바운드 Port다. */
    private final GetCaptureSessionUseCase getCaptureSessionUseCase;

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

    /*
     * 진행 중인 캡처 세션을 host 요청으로 일시정지한다.
     * 프론트는 마지막 청크 업로드와 CAP-07 완료 통보를 끝낸 뒤 이 API를 호출해야 한다.
     */
    @Operation(
            summary = "캡처 일시정지",
            description = "회의 개설자가 ACTIVE 캡처 세션을 PAUSED 상태로 전이합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/pause")
    public ApiResponse<CaptureSessionPauseResponse> pauseCaptureSession(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "캡처를 일시정지할 회의 식별자", required = true)
            @PathVariable Long meetingId
    ) {
        /* 토큰의 회사·구성원과 Path 회의 식별자만 사용해 일시정지 명령을 만든다. */
        PauseCaptureSessionCommand command = new PauseCaptureSessionCommand(
                principal.getCompanyId(),
                principal.getMemberId(),
                meetingId
        );

        /* PAUSED 상태 전이 결과를 명세의 200 공통 응답 형식으로 변환한다. */
        CaptureSessionPauseResult result = pauseCaptureSessionUseCase.pauseCaptureSession(command);
        return ApiResponse.success(
                "캡처를 일시정지했습니다.",
                CaptureSessionPauseResponse.from(result)
        );
    }

    /*
     * 일시정지된 캡처 세션을 host 요청으로 재개한다.
     * 새 세션을 만들지 않아 CAP-01에서 발급한 식별자와 공통 시간축을 그대로 유지한다.
     */
    @Operation(
            summary = "캡처 재개",
            description = "회의 개설자가 PAUSED 캡처 세션을 ACTIVE 상태로 전이합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/resume")
    public ApiResponse<CaptureSessionResumeResponse> resumeCaptureSession(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "캡처를 재개할 회의 식별자", required = true)
            @PathVariable Long meetingId
    ) {
        /* 토큰의 회사·구성원과 Path 회의 식별자만 사용해 재개 명령을 만든다. */
        ResumeCaptureSessionCommand command = new ResumeCaptureSessionCommand(
                principal.getCompanyId(),
                principal.getMemberId(),
                meetingId
        );

        /* ACTIVE 상태 전이 결과를 명세의 200 공통 응답 형식으로 변환한다. */
        CaptureSessionResumeResult result = resumeCaptureSessionUseCase.resumeCaptureSession(command);
        return ApiResponse.success(
                "캡처를 재개했습니다.",
                CaptureSessionResumeResponse.from(result)
        );
    }

    /*
     * 예약 참석자의 새로고침·재접속 화면 복구를 위해 현재 캡처 세션 상태를 조회한다.
     * 현재 녹음자와 세그먼트·청크 값은 A 도메인 조회 응답에서 별도로 결합한다.
     */
    @Operation(
            summary = "현재 캡처 세션 조회",
            description = "예약 참석자에게 현재 캡처 세션의 상태와 공통 시간축을 반환합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping
    public ApiResponse<CaptureSessionStateResponse> getCaptureSession(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "현재 캡처 세션을 조회할 회의 식별자", required = true)
            @PathVariable Long meetingId
    ) {
        /* 토큰의 회사·구성원과 Path 회의 식별자만 사용해 조회 조건을 만든다. */
        GetCaptureSessionQuery query = new GetCaptureSessionQuery(
                principal.getCompanyId(),
                principal.getMemberId(),
                meetingId
        );

        /* D 소유 캡처 세션 상태를 명세의 200 공통 응답 형식으로 변환한다. */
        CaptureSessionStateResult result = getCaptureSessionUseCase.getCaptureSession(query);
        return ApiResponse.success(
                "캡처 세션 조회에 성공했습니다.",
                CaptureSessionStateResponse.from(result)
        );
    }
}
