package com.module06.backend.cap.presentation.api;

import com.module06.backend.cap.application.usecase.GetActiveCaptureUseCase;
import com.module06.backend.cap.presentation.api.dto.response.ActiveCaptureResponse;
import com.module06.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Capture Query", description = "진행 중 캡처 조회(녹음자 이어받기) API")
@RestController
@RequestMapping("/api/captures")
public class CaptureQueryController {

    private final GetActiveCaptureUseCase getActiveCaptureUseCase;

    public CaptureQueryController(GetActiveCaptureUseCase getActiveCaptureUseCase) {
        this.getActiveCaptureUseCase = getActiveCaptureUseCase;
    }

    // 진행 중 캡처 조회 (CAP-09)
    @Operation(
            summary = "진행 중 캡처 조회",
            description = "토큰 사용자가 참석 중인 진행 중(IN_PROGRESS) 캡처를 반환합니다. 새로고침/재접속 시 복구 흐름의 "
                    + "첫 단추이며, canTakeover가 true면 다른 참석자가 이어받기(presign 호출)를 할 수 있습니다. "
                    + "진행 중인 캡처가 없으면 data는 null입니다."
    )
    // 참석자 전원 가능(명세). "누구나 로그인 사용자" 표현은 코드베이스 관용구인 base role 전부 나열로 맞춘다.
    // SecurityConfig의 authenticated()와 이중 방어 — anyRequest가 permitAll이라, 경로 등록을 빠뜨려도
    // 이 어노테이션이 컨트롤러에 붙어 있어 무방비 공개를 막는다(회의 참석자 스코프는 조회 쿼리가 담당).
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/active")
    public ApiResponse<ActiveCaptureResponse> active(
            @AuthenticationPrincipal(expression = "memberId") Long memberId) {
        // 파라미터 없음 — 참석 회의는 토큰에서 꺼낸 memberId 기준으로 서버가 찾는다(남의 진행 캡처 열람 차단).
        ActiveCaptureResponse data = getActiveCaptureUseCase.getActiveCapture(memberId)
                .map(ActiveCaptureResponse::from)
                .orElse(null);
        return ApiResponse.success("조회 성공", data);
    }
}
