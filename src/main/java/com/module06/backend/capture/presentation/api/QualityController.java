package com.module06.backend.capture.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.usecase.RegisterGoldSetUseCase;
import com.module06.backend.capture.application.usecase.RegisterGoldSetUseCase.RegisterGoldSetCommand;
import com.module06.backend.capture.presentation.api.request.RegisterGoldSetRequest;
import com.module06.backend.capture.presentation.api.response.GoldSetRegisteredResponse;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;

/*
 * QLTY — 품질 측정 장치.
 *
 * <h2>회의 경로가 아니다</h2>
 * 다른 캡처 API 는 `/api/meetings/{meetingId}` 아래인데 이쪽은 `/api/quality` 다. 회의 하나를
 * 다루는 것이 아니라 **여러 회의를 가로질러 재는** 자리이기 때문이다(QLTY-02·03 은 아예
 * meetingId 를 받지 않는다).
 *
 * <h2>관리자 기능이다</h2>
 * 정답지 동결은 되돌릴 수 없고, 그걸로 잰 수치가 프롬프트·모델 판단의 근거가 된다. 참석자
 * 아무나 얼릴 수 있으면 표본이 어떻게 뽑혔는지 아무도 모르게 된다.
 */
@Tag(name = "Quality", description = "품질 측정(gold set·지표·비용) API")
@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    private final RegisterGoldSetUseCase registerGoldSetUseCase;

    /*
     * QLTY-01 · gold set 등록.
     *
     * **측정 장치는 데이터가 쌓이기 전에 있어야 한다.** 없으면 프롬프트를 바꿔도 나아졌는지 알
     * 수 없고 정확도 개선이 감으로만 남는다. 3주 스코프에서는 5~10건으로 시작한다 —
     * 없는 것보다 작은 게 훨씬 낫다.
     */
    @Operation(
            summary = "gold set 등록 (QLTY-01)",
            description = "사람이 전량 검토한 회의를 정답지로 동결한다. 정답 라벨을 따로 받지 않는다 — "
                    + "그 회의의 지금 상태가 곧 정답이다. 미검토 액션이 남아 있으면 409 다: "
                    + "AI 출력이 정답지에 섞이면 모델이 자기 자신을 채점하게 된다. "
                    + "재라벨링은 기존 행을 고치지 않고 version 을 올린 새 행으로 쌓인다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PostMapping("/gold-set")
    public ApiResponse<GoldSetRegisteredResponse> registerGoldSet(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody RegisterGoldSetRequest request
    ) {
        return ApiResponse.created(
                "등록되었습니다.",
                GoldSetRegisteredResponse.from(registerGoldSetUseCase.register(
                        new RegisterGoldSetCommand(
                                me.getCompanyId(), request.meetingId(), me.getMemberId(), request.note()))));
    }
}
