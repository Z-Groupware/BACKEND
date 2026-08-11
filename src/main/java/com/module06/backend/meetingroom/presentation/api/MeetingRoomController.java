package com.module06.backend.meetingroom.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailability;
import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;
import com.module06.backend.meetingroom.application.usecase.GetMeetingRoomAvailabilityUseCase;
import com.module06.backend.meetingroom.application.usecase.GetMeetingRoomListUseCase;
import com.module06.backend.meetingroom.presentation.api.request.MeetingRoomAvailabilityRequest;
import com.module06.backend.meetingroom.presentation.api.response.MeetingRoomAvailabilityResponse;
import com.module06.backend.meetingroom.presentation.api.response.MeetingRoomListResponse;

/*
 * 회의실 REST API의 진입점이다.
 *
 * ROOM-01에서는 Access Token으로 인증된 구성원의 회사 식별자를 사용해 활성 회의실 목록을 조회하고,
 * ROOM-02에서는 같은 회사 범위에서 특정 날짜의 30분 슬롯 예약 현황을 조회한다.
 * companyId는 URL이나 Query Parameter로 받지 않아 다른 회사의 데이터를 임의로 요청할 수 없게 한다.
 * 실제 JWT principal 구현은 identity 담당 영역이며 이 Controller는 companyId·memberId 프로퍼티 계약만 사용한다.
 */
@Tag(name = "Meeting Room", description = "회의실 조회 및 관리 API")
@RestController
@RequestMapping("/api/v1/meeting-rooms")
@RequiredArgsConstructor
public class MeetingRoomController {

    /* ROOM-01 기능 구현체와 Controller 사이의 인바운드 포트다. */
    private final GetMeetingRoomListUseCase getMeetingRoomListUseCase;

    /* ROOM-02 기능 구현체와 Controller 사이의 인바운드 포트다. */
    private final GetMeetingRoomAvailabilityUseCase getMeetingRoomAvailabilityUseCase;

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
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
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

    /*
     * 회의실 하나의 월요일부터 금요일까지 30분 슬롯 예약 현황을 조회한다.
     * 주간 시간표 전체를 이 응답 하나로 그릴 수 있게 회의실 정보와 날짜별 예약 상태를 함께 내려준다.
     *
     * @param companyId 인증 principal에서 추출한 요청자의 회사 식별자
     * @param memberId 인증 principal에서 추출한 요청자의 구성원 식별자
     * @param date 조회 주의 기준일 문자열, 생략하면 KST 오늘
     * @param meetingRoomId 조회할 필수 회의실 식별자 문자열
     * @return 공통 성공 응답으로 감싼 단일 회의실 주간 슬롯 현황
     */
    @Operation(
            summary = "회의실 예약 현황 조회",
            description = "회의실 하나의 월요일부터 금요일까지 30분 슬롯 예약 현황을 조회합니다. "
                    + "이용 가능 시간 밖의 슬롯은 응답에 포함하지 않으며, 참석자가 아닌 회의의 제목은 노출하지 않습니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/availability")
    public ApiResponse<MeetingRoomAvailabilityResponse> getMeetingRoomAvailability(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Parameter(description = "조회 주의 기준일 (YYYY-MM-DD), 생략 시 KST 오늘", example = "2026-08-10")
            @RequestParam(name = "date", required = false) String date,
            @Parameter(description = "주간 현황을 조회할 회의실 식별자", required = true, example = "2")
            @RequestParam(name = "meetingRoomId", required = false) String meetingRoomId
    ) {
        /* Query Parameter를 요청 DTO에서 검증하고 파싱한다. */
        MeetingRoomAvailabilityRequest request = MeetingRoomAvailabilityRequest.of(date, meetingRoomId);

        /* 인증 정보와 조회 조건을 합쳐 예약 현황 조회 유스케이스를 실행한다. */
        MeetingRoomAvailability availability = getMeetingRoomAvailabilityUseCase
                .getMeetingRoomAvailability(request.toQuery(companyId, memberId));

        /* 애플리케이션 결과를 API 응답 DTO로 변환하고 프로젝트 공통 성공 래퍼로 반환한다. */
        return ApiResponse.success(
                "회의실 예약 현황 조회에 성공했습니다.",
                MeetingRoomAvailabilityResponse.from(availability)
        );
    }
}
