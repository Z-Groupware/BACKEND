package com.module06.backend.cap.presentation.api;

import com.module06.backend.cap.application.usecase.SubmitCaptionsUseCase;
import com.module06.backend.cap.presentation.api.dto.request.SubmitCaptionsRequest;
import com.module06.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Caption", description = "회의 실시간 자막(caption_chunk) API")
@RestController
@RequestMapping("/api/meetings/{meetingId}/captions")
public class CaptionController {

    private final SubmitCaptionsUseCase submitCaptionsUseCase;

    public CaptionController(SubmitCaptionsUseCase submitCaptionsUseCase) {
        this.submitCaptionsUseCase = submitCaptionsUseCase;
    }

    // 자막 청크 배치 전송 (CAP-11)
    @Operation(
            summary = "자막 청크 배치 전송",
            description = "참석자 브라우저가 인식한 자막 조각을 배치로 전송한다. 정본 아님 — 실시간 표시 + "
                    + "STT 실패 폴백용. 이미 전송된 (meetingId, memberId, seq) 조합은 재전송으로 보고 조용히 건너뛴다."
    )
    // 참석자 전원 가능(Host 전용 아님) — 서비스의 isAttendee가 검증한다.
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> submit(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody SubmitCaptionsRequest request) {
        // personId(memberId)는 JWT principal에서 꺼낸다 — 본문으로 받으면 남의 이름으로 발화를 심을 수 있다.
        submitCaptionsUseCase.submitCaptions(request.toCommand(meetingId, memberId));
        return ApiResponse.accepted("자막이 저장·전달되었습니다.", null);
    }
}
