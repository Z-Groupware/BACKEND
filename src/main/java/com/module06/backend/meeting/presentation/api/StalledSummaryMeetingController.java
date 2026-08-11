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
import com.module06.backend.meeting.application.query.GetStalledSummaryMeetingsQuery;
import com.module06.backend.meeting.application.result.StalledSummaryMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetStalledSummaryMeetingsUseCase;
import com.module06.backend.meeting.presentation.api.response.StalledSummaryMeetingListResponse;

/*
 * MEET-15 요약 중단·실패 회의 목록 REST API의 진입점이다.
 *
 * 회사와 로그인 구성원은 인증 principal에서만 읽어 다른 사용자의 처리 목록을 요청할 수 없게 한다.
 */
@Tag(name = "Meeting", description = "회의 예약 및 진행 API")
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class StalledSummaryMeetingController {

    /* MEET-15 프레젠테이션 계층과 조회 서비스 사이의 인바운드 Port다. */
    private final GetStalledSummaryMeetingsUseCase getStalledSummaryMeetingsUseCase;

    /* 로그인 사용자가 개설한 종료 회의 중 요약이 중단되거나 실패한 회의를 조회한다. */
    @Operation(
            summary = "요약 중단·실패 회의 목록 조회",
            description = "로그인 사용자가 개설한 종료 회의 중 AI 요약이 중단되거나 실패한 회의를 조회합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/stalled-summaries")
    public ApiResponse<StalledSummaryMeetingListResponse> getStalledSummaryMeetings(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "프로젝트 식별자", example = "12")
            @RequestParam(required = false) Long projectId,
            @Parameter(description = "회의 시작일 하한", example = "2026-08-01")
            @RequestParam(required = false) String from,
            @Parameter(description = "회의 시작일 상한", example = "2026-08-31")
            @RequestParam(required = false) String to,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") String page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") String size
    ) {
        /* 인증 값과 파싱한 필터를 Query로 묶어 MEET-15 유스케이스를 실행한다. */
        StalledSummaryMeetingListResult result = getStalledSummaryMeetingsUseCase
                .getStalledSummaryMeetings(new GetStalledSummaryMeetingsQuery(
                        principal.getCompanyId(),
                        principal.getMemberId(),
                        projectId,
                        parseDate(from, "from"),
                        parseDate(to, "to"),
                        parseInteger(page, "page"),
                        parseInteger(size, "size")
                ));

        /* 결과가 없어도 페이지 메타와 빈 배열을 포함하는 공통 200 응답으로 반환한다. */
        return ApiResponse.success(
                "요약 중단 회의 목록을 조회했습니다.",
                StalledSummaryMeetingListResponse.from(result)
        );
    }

    /* 선택 날짜 문자열을 ISO 날짜로 변환하고 잘못된 형식은 공통 입력 오류 대상으로 바꾼다. */
    private LocalDate parseDate(String value, String parameterName) {
        /* 파라미터 생략은 날짜 제한을 두지 않기 위해 null로 유지한다. */
        if (value == null || value.isBlank()) {
            return null;
        }

        /* yyyy-MM-dd 형식만 허용하고 다른 입력은 IllegalArgumentException으로 변환한다. */
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(parameterName + "은 yyyy-MM-dd 형식이어야 합니다.", exception);
        }
    }

    /* 페이지 문자열을 정수로 변환하고 숫자가 아닌 입력을 공통 입력 오류 대상으로 바꾼다. */
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
