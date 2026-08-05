package com.module06.backend.capture.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.usecase.GetProcessingStatusUseCase;
import com.module06.backend.capture.application.usecase.GetSummaryUseCase;
import com.module06.backend.capture.application.usecase.RunAnalysisUseCase;
import com.module06.backend.capture.presentation.api.response.AnalysisRunResponse;
import com.module06.backend.capture.presentation.api.response.MeetingSummaryResponse;
import com.module06.backend.capture.presentation.api.response.ProcessingStatusResponse;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;

/*
 * 회의 분석 REST API 진입점이다(ANLZ-01 · CAP-06 · ANLZ-03).
 *
 * <h2>companyId 는 토큰에서만 온다</h2>
 * URL·쿼리·본문으로 받지 않는다. 받으면 값을 바꿔 다른 회사 데이터에 접근할 수 있다 —
 * 프로젝트·인수인계 도메인에서 실제로 그 구멍이 발견돼 걷어냈다(#101 · #102).
 *
 * <h2>세 층이 각각 다른 것을 본다</h2>
 * <ul>
 *   <li>SecurityConfig — "로그인했나". PR #108 이후 <b>기본이 잠김</b>이라 새 경로를 따로
 *       등록하지 않는다(예전에는 등록하지 않으면 익명 요청이 principal 없이 들어왔다)</li>
 *   <li>{@code @PreAuthorize} — "그 역할인가"</li>
 *   <li>{@code MeetingAccessGuard} — <b>"이 회의가 그 사람 회사 것인가"</b>. 앞의 둘은 이걸
 *       보지 않는다. 로그인한 사원이 남의 회사 회의 id 를 넣는 것은 서비스 층이 막는다</li>
 * </ul>
 */
@Tag(name = "Meeting Analysis", description = "회의 분석 실행·상태·요약 API")
@RestController
@RequestMapping("/api/meetings/{meetingId}")
@RequiredArgsConstructor
public class AnalysisController {

    private final RunAnalysisUseCase runAnalysisUseCase;
    private final GetProcessingStatusUseCase getProcessingStatusUseCase;
    private final GetSummaryUseCase getSummaryUseCase;

    /*
     * ANLZ-01 · 요약 수동 실행·강제 재실행.
     *
     * 기본 경로가 아니다. 분석은 회의 종료에서 자동으로 시작되고, 이 API 는 자동 실행이
     * 스킵된 회의를 수동으로 돌리거나 완료된 분석을 강제로 다시 돌릴 때만 쓴다.
     */
    @Operation(
            summary = "회의 분석 실행 (ANLZ-01)",
            description = "회의의 발화를 계층 파이프라인에 태워 요약을 만든다. "
                    + "force=true 는 이미 완료된 분석을 다시 돌리며 재과금이 발생한다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/analysis")
    public ApiResponse<AnalysisRunResponse> runAnalysis(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return ApiResponse.success(
                "분석을 실행했습니다.",
                AnalysisRunResponse.from(runAnalysisUseCase.run(me.getCompanyId(), meetingId, force)));
    }

    /* CAP-06 · AI 처리 상태 조회. 사용자가 "어디까지 됐는지"를 보는 유일한 경로다. */
    @Operation(
            summary = "AI 처리 상태 조회 (CAP-06)",
            description = "계층별 실행 상태와 누적 토큰을 조회한다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/processing-status")
    public ApiResponse<ProcessingStatusResponse> getProcessingStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId
    ) {
        return ApiResponse.success(
                "처리 상태를 조회했습니다.",
                ProcessingStatusResponse.from(
                        getProcessingStatusUseCase.getProcessingStatus(me.getCompanyId(), meetingId)));
    }

    /* ANLZ-03 · 요약 조회. 분석 전이면 404 다 — 빈 요약을 지어내지 않는다. */
    @Operation(
            summary = "회의 요약 조회 (ANLZ-03)",
            description = "주제별 결정·논의·블로커와 각 항목의 근거 발화를 함께 조회한다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/summary")
    public ApiResponse<MeetingSummaryResponse> getSummary(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId
    ) {
        return ApiResponse.success(
                "회의 요약을 조회했습니다.",
                MeetingSummaryResponse.from(getSummaryUseCase.getSummary(me.getCompanyId(), meetingId)));
    }
}
