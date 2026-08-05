package com.module06.backend.capture.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.usecase.RunAnalysisUseCase;
import com.module06.backend.capture.presentation.api.response.AnalysisRunResponse;
import com.module06.backend.global.response.ApiResponse;

/*
 * 회의 분석 REST API 진입점이다(ANLZ-01).
 *
 * companyId 는 URL·쿼리로 받지 않고 인증 principal 에서만 꺼낸다 — 다른 회사의 회의를
 * 임의로 요청할 수 없게 하는 것이 목적이다(회의실 도메인과 같은 규약).
 *
 * ⚠ 이 경로들은 SecurityConfig 에 등록돼야 인증이 걸린다. 현재 체인이
 * anyRequest().permitAll() 로 끝나므로, 등록하지 않으면 @PreAuthorize 만으로는
 * 익명 요청이 principal 없이 들어와 NPE 로 500 이 난다.
 */
@Tag(name = "Meeting Analysis", description = "회의 분석 실행·상태·요약 API")
@RestController
@RequestMapping("/api/meetings/{meetingId}")
@RequiredArgsConstructor
public class AnalysisController {

    private final RunAnalysisUseCase runAnalysisUseCase;

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
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long meetingId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return ApiResponse.success(
                "분석을 실행했습니다.",
                AnalysisRunResponse.from(runAnalysisUseCase.run(companyId, meetingId, force)));
    }
}
