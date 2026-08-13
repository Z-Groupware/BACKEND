package com.module06.backend.meetingroom.presentation.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.module06.backend.meetingroom.application.command.DeactivateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.result.MeetingRoomCreationResult;
import com.module06.backend.meetingroom.application.result.MeetingRoomUpdateResult;
import com.module06.backend.meetingroom.application.usecase.CreateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.DeactivateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.UpdateMeetingRoomUseCase;
import com.module06.backend.meetingroom.presentation.api.request.CreateMeetingRoomRequest;
import com.module06.backend.meetingroom.presentation.api.request.UpdateMeetingRoomRequest;
import com.module06.backend.meetingroom.presentation.api.response.CreateMeetingRoomResponse;
import com.module06.backend.meetingroom.presentation.api.response.UpdateMeetingRoomResponse;

/*
 * 회의실 등록·수정·비활성화 명령 REST API의 진입점이다.
 *
 * ROOM-03은 OWNER·ADMIN만 열 수 있으며 companyId는 Access Token principal에서만 읽는다.
 */
@Tag(name = "Meeting Room", description = "회의실 조회 및 관리 API")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class MeetingRoomCommandController {

    /* ROOM-03 프레젠테이션 계층과 등록 서비스 사이의 인바운드 Port다. */
    private final CreateMeetingRoomUseCase createMeetingRoomUseCase;

    /* ROOM-04 프레젠테이션 계층과 수정 서비스 사이의 인바운드 Port다. */
    private final UpdateMeetingRoomUseCase updateMeetingRoomUseCase;

    /* ROOM-05 프레젠테이션 계층과 비활성화 서비스 사이의 인바운드 Port다. */
    private final DeactivateMeetingRoomUseCase deactivateMeetingRoomUseCase;

    /* 인증 사용자의 회사에 새로운 활성 회의실을 등록한다. */
    @Operation(
            summary = "회의실 등록",
            description = "로그인한 사용자의 회사에 30분 단위 이용 시간을 가진 회의실을 등록합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<CreateMeetingRoomResponse> createMeetingRoom(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Valid @RequestBody CreateMeetingRoomRequest request
    ) {
        /* 인증 회사와 검증된 본문을 명령으로 결합해 ROOM-03 유스케이스를 실행한다. */
        MeetingRoomCreationResult result = createMeetingRoomUseCase.createMeetingRoom(
                request.toCommand(companyId)
        );

        /* 실제 HTTP와 본문의 상태를 모두 201로 맞춘 생성 성공 응답을 반환한다. */
        return ApiResponse.created(
                "회의실을 등록했습니다.",
                CreateMeetingRoomResponse.from(result)
        );
    }

    /* OWNER·ADMIN이 자기 회사의 활성 회의실 정보를 부분 수정한다. */
    @Operation(
            summary = "회의실 정보 수정",
            description = "회의실 이름·위치·이용 가능 시간을 부분 수정합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PatchMapping("/{meetingRoomId}")
    public ApiResponse<UpdateMeetingRoomResponse> updateMeetingRoom(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal me,
            @Parameter(description = "수정할 회의실 식별자", required = true, example = "2")
            @PathVariable Long meetingRoomId,
            @Valid @RequestBody UpdateMeetingRoomRequest request
    ) {
        /* 인증 토큰의 회사·역할과 Path·본문을 결합해 ROOM-04 유스케이스를 실행한다. */
        MeetingRoomUpdateResult result = updateMeetingRoomUseCase.updateMeetingRoom(
                request.toCommand(me.getCompanyId(), me.getAuthority(), meetingRoomId)
        );

        /* 수정된 전체 회의실 상태를 명세의 200 OK 공통 응답으로 반환한다. */
        return ApiResponse.success(
                "회의실 정보를 수정했습니다.",
                UpdateMeetingRoomResponse.from(result)
        );
    }

    /* OWNER·ADMIN이 미래 예약이 없는 자기 회사의 활성 회의실을 비활성화한다. */
    @Operation(
            summary = "회의실 비활성화",
            description = "미래 예정 예약이 없는 활성 회의실을 소프트 삭제합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @DeleteMapping("/{meetingRoomId}")
    public ApiResponse<Void> deactivateMeetingRoom(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal me,
            @Parameter(description = "비활성화할 회의실 식별자", required = true, example = "2")
            @PathVariable Long meetingRoomId
    ) {
        /* 인증 토큰의 회사·역할과 Path 식별자를 결합해 ROOM-05 유스케이스를 실행한다. */
        deactivateMeetingRoomUseCase.deactivateMeetingRoom(new DeactivateMeetingRoomCommand(
                me.getCompanyId(),
                me.getAuthority(),
                meetingRoomId
        ));

        /* 삭제된 표현을 반환하지 않고 명세의 200 OK와 빈 data 성공 응답을 반환한다. */
        return ApiResponse.successWithoutData("회의실을 비활성화했습니다.");
    }
}
