package com.module06.backend.cap.presentation.api;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.usecase.GetCaptionsUseCase;
import com.module06.backend.cap.application.usecase.SubmitCaptionsUseCase;
import com.module06.backend.cap.application.usecase.SubscribeToCaptionsUseCase;
import com.module06.backend.cap.presentation.api.dto.request.SubmitCaptionsRequest;
import com.module06.backend.cap.presentation.api.dto.response.CaptionsResponse;
import com.module06.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Caption", description = "회의 실시간 자막(caption_chunk) API")
@RestController
@RequestMapping("/api/meetings/{meetingId}/captions")
public class CaptionController {

    private final SubmitCaptionsUseCase submitCaptionsUseCase;
    private final GetCaptionsUseCase getCaptionsUseCase;
    private final SubscribeToCaptionsUseCase subscribeToCaptionsUseCase;

    public CaptionController(SubmitCaptionsUseCase submitCaptionsUseCase, GetCaptionsUseCase getCaptionsUseCase,
                             SubscribeToCaptionsUseCase subscribeToCaptionsUseCase) {
        this.submitCaptionsUseCase = submitCaptionsUseCase;
        this.getCaptionsUseCase = getCaptionsUseCase;
        this.subscribeToCaptionsUseCase = subscribeToCaptionsUseCase;
    }

    // 자막 청크 배치 전송 (CAP-11)
    @Operation(
            summary = "자막 청크 배치 전송",
            description = "host 브라우저가 인식한 자막 조각을 배치로 전송한다. 정본 아님 — 실시간 표시 + "
                    + "STT 실패 폴백용. 이미 전송된 (meetingId, memberId, seq) 조합은 재전송으로 보고 조용히 건너뛴다."
    )
    // host 전용 — 녹음이 host 한 명의 기기로만 이뤄지므로 자막도 host만 보낸다. 서비스의 isHost가 검증한다.
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

    // 자막 전체 조회 (CAP-12)
    @Operation(
            summary = "자막 전체 조회",
            description = "이 회의에 쌓인 자막 전체를 시간순으로 반환한다(백필용). SSE(CAP-13)는 구독 시점 이후 "
                    + "분만 push하므로, 그 이전 분은 이 API로 먼저 채운 뒤 이어받는다. "
                    + "회의 참석자, 같은 회사의 owner/admin, 또는 프로젝트 멤버만 가능합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping
    public ApiResponse<CaptionsResponse> list(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @AuthenticationPrincipal(expression = "teamId") Long teamId,
            @AuthenticationPrincipal(expression = "authority") String role,
            @AuthenticationPrincipal(expression = "isAdmin") boolean isAdmin) {
        // 요청자 신원은 JWT principal에서 꺼낸다. 열람 권한(참석자/owner·admin/프로젝트 멤버)은 access-guard가 판정.
        CapMeetingAccessGuard.ViewerContext requester =
                new CapMeetingAccessGuard.ViewerContext(memberId, companyId, teamId, role, isAdmin);
        GetCaptionsUseCase.Result result = getCaptionsUseCase.getCaptions(meetingId, requester);
        return ApiResponse.success("조회 성공", CaptionsResponse.from(result));
    }

    // 자막 실시간 구독 SSE (CAP-13)
    @Operation(
            summary = "자막 실시간 구독(SSE)",
            description = "host만 구독할 수 있다. host가 아닌 요청자는 CAP_NOT_HOST(403)로 거절된다. "
                    + "이 회의로 들어오는 자막(CAP-11)을 실시간으로 받는다. JSON 단발 응답이 아니라 SSE "
                    + "스트림이다. event: caption·participant·heartbeat 세 종류를 내려준다. "
                    + "구독 시점 이전 자막은 안 내려주므로, 자막 전체 조회(CAP-12)로 먼저 백필해야 한다."
    )
    // host 전용 — 서비스의 isHost가 검증한다.
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId) {
        // 요청자는 JWT principal에서 꺼낸다(다른 CAP 엔드포인트와 동일 원칙). SSE라 ApiResponse로 감싸지 않는다.
        return subscribeToCaptionsUseCase.subscribeToCaptions(meetingId, memberId);
    }
}
