package com.module06.backend.calendar.presentation.api;

import java.time.YearMonth;
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

import com.module06.backend.calendar.application.usecase.GetCalendarUseCase;
import com.module06.backend.calendar.presentation.api.response.CalendarItemResponse;
import com.module06.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/* comment.
    캘린더 통합조회 API 진입점. OWNER: 본인이 만든 프로젝트 기간 / LEADER·MEMBER: 본인 개인
    액션 마감일+개인 Todo(2026-08-06 Figma 확인). 역할 판단은 접근 제어(@PreAuthorize)가
    아니라 응답 내용 분기이므로, authority를 그대로 유스케이스에 넘긴다.

    담당 엔드포인트
    - GET /api/calendar?month=yyyy-MM   통합 조회 (역할별 분기, month 생략 시 이번 달)

    연결된 클래스
    - GetCalendarUseCase    : 호출 대상
    - CalendarItemResponse  : 출력 DTO
*/
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Tag(name = "Calendar", description = "캘린더·개인 Todo API")
public class CalendarController {

    private final GetCalendarUseCase getCalendarUseCase;

    @Operation(summary = "캘린더 통합 조회", description = "역할별 분기 — OWNER: 본인 프로젝트 기간 / LEADER·MEMBER: 개인 액션 마감일+Todo.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<CalendarItemResponse>> getCalendar(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "authority") String authority,
            @RequestParam(required = false) String month
    ) {
        YearMonth targetMonth = month == null ? YearMonth.now() : YearMonth.parse(month);

        List<CalendarItemResponse> items = getCalendarUseCase
                .getCalendar(companyId, memberId, authority, targetMonth).stream()
                .map(CalendarItemResponse::from)
                .toList();

        return ApiResponse.success("캘린더를 조회했습니다.", items);
    }
}
