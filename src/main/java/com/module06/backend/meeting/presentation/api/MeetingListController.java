package com.module06.backend.meeting.presentation.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.query.GetMeetingListQuery;
import com.module06.backend.meeting.application.result.MeetingListResult;
import com.module06.backend.meeting.application.usecase.GetMeetingListUseCase;
import com.module06.backend.meeting.domain.model.MeetingListScope;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.presentation.api.response.MeetingListResponse;

/*
 * MEET-02 회의 목록 필터 조회 REST API의 진입점이다.
 *
 * 회사·구성원·권한은 인증 principal에서만 읽고, 사용자가 조작할 수 있는 요청에는
 * 회의 목록의 표시 필터와 페이지 값만 허용한다.
 */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingListController {

    /* MEET-02 프레젠테이션 계층과 조회 서비스 사이의 인바운드 Port다. */
    private final GetMeetingListUseCase getMeetingListUseCase;

    /* 로그인 사용자의 열람 범위 안에서 필터와 페이징이 적용된 회의 목록을 조회한다. */
    @Operation(
            summary = "회의 목록 조회",
            description = "프로젝트·회의실·기간·상태 필터로 열람 가능한 회의를 조회합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping
    public ApiResponse<MeetingListResponse> getMeetings(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "프로젝트 식별자", example = "12")
            @RequestParam(required = false) Long projectId,
            @Parameter(description = "회의실 식별자", example = "2")
            @RequestParam(required = false) Long meetingRoomId,
            @Parameter(description = "조회 시작 날짜", example = "2026-05-07")
            @RequestParam(required = false) String from,
            @Parameter(description = "조회 종료 날짜", example = "2026-08-07")
            @RequestParam(required = false) String to,
            @Parameter(description = "회의 상태", example = "DONE")
            @RequestParam(required = false) String status,
            @Parameter(description = "회의 화면 탭. HOSTED=내가 개설한 · ATTENDING=참여해야 할", example = "HOSTED")
            @RequestParam(required = false) String scope,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") String page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") String size
    ) {
        /* OWNER 또는 관리자에게만 회사 전체 열람 플래그를 부여하고 나머지는 참여 범위로 제한한다. */
        boolean companyWideRead = principal.isAdmin() || "OWNER".equals(principal.getAuthority());

        /* 인증 값과 파싱한 선택 필터를 Query로 묶어 MEET-02 유스케이스를 실행한다. */
        MeetingListResult result = getMeetingListUseCase.getMeetings(new GetMeetingListQuery(
                principal.getCompanyId(),
                principal.getMemberId(),
                companyWideRead,
                projectId,
                meetingRoomId,
                parseDate(from, "from"),
                parseDate(to, "to"),
                parseStatus(status),
                parseScope(scope),
                parseInteger(page, "page"),
                parseInteger(size, "size")
        ));

        /* 빈 결과도 meetings 빈 배열과 page 메타를 포함하는 공통 200 응답으로 반환한다. */
        return ApiResponse.success(
                "회의 목록 조회에 성공했습니다.",
                MeetingListResponse.from(result)
        );
    }

    /* 선택 날짜 문자열을 ISO 날짜로 변환하고 잘못된 형식은 Z-001 처리 대상으로 바꾼다. */
    private LocalDate parseDate(String value, String parameterName) {
        /* 파라미터 생략은 서비스의 기본 최근 3개월 계산을 위해 null로 유지한다. */
        if (value == null || value.isBlank()) {
            return null;
        }

        /* yyyy-MM-dd 형식만 허용하고 다른 입력은 공통 IllegalArgumentException으로 변환한다. */
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(parameterName + "은 yyyy-MM-dd 형식이어야 합니다.", exception);
        }
    }

    /* 선택 상태 문자열을 회의 생명주기 enum으로 변환한다. */
    private MeetingStatus parseStatus(String value) {
        /* 상태 생략은 모든 상태를 조회하기 위해 null로 유지한다. */
        if (value == null || value.isBlank()) {
            return null;
        }

        /* 확정된 대문자 상태 외의 값은 Z-001 처리 대상인 입력 오류로 변환한다. */
        try {
            return MeetingStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status 값이 올바르지 않습니다.", exception);
        }
    }

    /* 선택 scope 문자열을 회의 화면 탭 enum으로 변환한다. */
    private MeetingListScope parseScope(String value) {
        /* scope 생략은 역할 기반 열람 범위 전체를 조회하기 위해 null로 유지한다. */
        if (value == null || value.isBlank()) {
            return null;
        }

        /* HOSTED·ATTENDING 외의 값은 Z-001 처리 대상인 입력 오류로 변환한다. */
        try {
            return MeetingListScope.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("scope 값이 올바르지 않습니다.", exception);
        }
    }

    /* 페이지 문자열을 정수로 변환하고 숫자가 아닌 입력을 Z-001 처리 대상으로 바꾼다. */
    private Integer parseInteger(String value, String parameterName) {
        /* defaultValue가 적용되지만 직접 호출에서도 null을 명확한 입력 오류로 처리한다. */
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + "은 정수여야 합니다.");
        }

        /* 정수 표현만 허용하고 실제 허용 범위는 서비스에서 일관되게 검증한다. */
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(parameterName + "은 정수여야 합니다.", exception);
        }
    }
}
