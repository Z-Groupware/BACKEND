package com.module06.backend.meeting.presentation.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.EnterMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingCreationResult;
import com.module06.backend.meeting.application.result.MeetingEntryResult;
import com.module06.backend.meeting.application.usecase.CreateMeetingUseCase;
import com.module06.backend.meeting.application.usecase.EnterMeetingUseCase;
import com.module06.backend.meeting.presentation.api.request.CreateMeetingRequest;
import com.module06.backend.meeting.presentation.api.response.CreateMeetingResponse;
import com.module06.backend.meeting.presentation.api.response.MeetingEntryResponse;

/*
 * 회의 REST API의 진입점이다.
 *
 * MEET-01에서는 인증 principal의 회사·구성원·팀 식별자와 요청 본문을 합쳐 회의 예약 유스케이스를 실행한다.
 * companyId와 hostMemberId를 본문으로 받지 않아 다른 회사 또는 다른 사용자를 가장한 예약을 차단한다.
 */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    /* MEET-01 프레젠테이션 계층과 애플리케이션 계층 사이의 인바운드 포트다. */
    private final CreateMeetingUseCase createMeetingUseCase;

    /* MEET-07 프레젠테이션 계층과 회의 입장 서비스 사이의 인바운드 Port다. */
    private final EnterMeetingUseCase enterMeetingUseCase;

    /*
     * 회의실, 시간, 프로젝트와 참석자를 확정해 새 회의를 예약한다.
     * 개설자는 요청 참석자 목록에 없어도 유스케이스에서 자동으로 포함한다.
     *
     * @param companyId 인증 principal에서 추출한 회사 식별자
     * @param memberId 인증 principal에서 추출한 개설자 식별자
     * @param teamId 인증 principal에서 추출한 개설자 소속 팀 식별자
     * @param request 검증된 회의 예약 요청 본문
     * @return 공통 생성 성공 응답으로 감싼 회의 예약 결과
     */
    @Operation(
            summary = "회의 개설",
            description = "활성 회의실의 30분 슬롯을 예약하고 개설자를 포함한 참석자 명단을 확정합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<CreateMeetingResponse> createMeeting(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "teamId") Long teamId,
            @Valid @RequestBody CreateMeetingRequest request
    ) {
        /* 인증 정보와 요청 값을 결합한 명령으로 회의 예약 유스케이스를 실행한다. */
        MeetingCreationResult result = createMeetingUseCase.createMeeting(
                request.toCommand(companyId, memberId, teamId)
        );

        /* 201 Created 상태와 명세의 응답 data를 프로젝트 공통 래퍼로 반환한다. */
        return ApiResponse.created(
                "회의를 예약했습니다.",
                CreateMeetingResponse.from(result)
        );
    }

    /* 예약 참석자를 회의에 입장시키고 최초 입장이면 회의를 진행 상태로 전이한다. */
    @Operation(
            summary = "회의 입장",
            description = "예약된 참석자가 허용 시간에 입장하며 첫 입장이면 회의를 시작합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/{meetingId}/entry")
    public ApiResponse<MeetingEntryResponse> enterMeeting(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal me,
            @Parameter(description = "입장할 회의 식별자", required = true, example = "91")
            @PathVariable Long meetingId
    ) {
        /* 인증 토큰의 회사·구성원과 Path 식별자를 결합해 MEET-07 유스케이스를 실행한다. */
        MeetingEntryResult result = enterMeetingUseCase.enterMeeting(new EnterMeetingCommand(
                me.getCompanyId(),
                me.getMemberId(),
                meetingId
        ));

        /* 입장 후 상태와 최초 시작 시각, 화면 분기 값을 명세의 200 성공 응답으로 반환한다. */
        return ApiResponse.success(
                "회의에 입장했습니다.",
                MeetingEntryResponse.from(result)
        );
    }
}
