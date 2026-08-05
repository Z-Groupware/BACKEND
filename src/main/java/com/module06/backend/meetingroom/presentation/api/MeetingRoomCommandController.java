package com.module06.backend.meetingroom.presentation.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.module06.backend.meetingroom.application.result.MeetingRoomCreationResult;
import com.module06.backend.meetingroom.application.usecase.CreateMeetingRoomUseCase;
import com.module06.backend.meetingroom.presentation.api.request.CreateMeetingRoomRequest;
import com.module06.backend.meetingroom.presentation.api.response.CreateMeetingRoomResponse;

/*
 * 회의실 등록·수정·비활성화 명령 REST API의 진입점이다.
 *
 * ROOM-03은 모든 인증 역할에 열려 있으며 companyId는 Access Token principal에서만 읽는다.
 */
@Tag(name = "Meeting Room", description = "회의실 조회 및 관리 API")
@RestController
@RequestMapping("/api/v1/meeting-rooms")
@RequiredArgsConstructor
public class MeetingRoomCommandController {

    /* ROOM-03 프레젠테이션 계층과 등록 서비스 사이의 인바운드 Port다. */
    private final CreateMeetingRoomUseCase createMeetingRoomUseCase;

    /* 인증 사용자의 회사에 새로운 활성 회의실을 등록한다. */
    @Operation(
            summary = "회의실 등록",
            description = "로그인한 사용자의 회사에 30분 단위 이용 시간을 가진 회의실을 등록합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
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
}
