package com.module06.backend.meetingroom.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;
import com.module06.backend.meetingroom.application.usecase.GetMeetingRoomListUseCase;
import com.module06.backend.meetingroom.presentation.api.response.MeetingRoomListResponse;

/*
 * 회의실 REST API의 진입점이다.
 *
 * ROOM-01에서는 Access Token으로 인증된 구성원의 회사 식별자를 사용해 활성 회의실 목록을 조회한다.
 * companyId는 URL이나 Query Parameter로 받지 않아 다른 회사의 데이터를 임의로 요청할 수 없게 한다.
 * 실제 JWT principal 구현은 identity 담당 영역이며 이 Controller는 companyId 프로퍼티 계약만 사용한다.
 */
@Tag(name = "Meeting Room", description = "회의실 조회 및 관리 API")
@RestController
@RequestMapping("/api/v1/meeting-rooms")
@RequiredArgsConstructor
public class MeetingRoomController {

    /* ROOM-01 기능 구현체와 Controller 사이의 인바운드 포트다. */
    private final GetMeetingRoomListUseCase getMeetingRoomListUseCase;

    /*
     * 요청자가 속한 회사에서 현재 활성화된 회의실 목록을 조회한다.
     * 조회 결과가 없으면 404 대신 빈 meetingRooms 배열을 포함한 200 OK 응답을 반환한다.
     *
     * @param companyId 인증 principal에서 추출한 요청자의 회사 식별자
     * @return 공통 성공 응답으로 감싼 회의실 목록
     */
    @Operation(
            summary = "회의실 목록 조회",
            description = "로그인한 사용자의 회사에서 비활성화되지 않은 회의실 목록을 이름순으로 조회합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'LEADER', 'MEMBER')")
    @GetMapping
    public ApiResponse<MeetingRoomListResponse> getMeetingRooms(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId
    ) {
        /* 인증된 회사 범위로 조회 유스케이스를 실행한다. */
        List<MeetingRoomSummary> summaries = getMeetingRoomListUseCase.getMeetingRooms(companyId);

        /* 애플리케이션 결과를 API 응답 DTO로 변환하고 프로젝트 공통 성공 래퍼로 반환한다. */
        return ApiResponse.success(
                "회의실 목록 조회에 성공했습니다.",
                MeetingRoomListResponse.from(summaries)
        );
    }
}
