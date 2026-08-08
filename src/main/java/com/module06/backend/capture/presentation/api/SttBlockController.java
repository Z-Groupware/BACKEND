package com.module06.backend.capture.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.usecase.GetSttBlocksUseCase;
import com.module06.backend.capture.application.usecase.RetrySttBlockUseCase;
import com.module06.backend.capture.application.usecase.RetrySttBlockUseCase.RetrySttBlockCommand;
import com.module06.backend.capture.presentation.api.request.SttBlockRetryRequest;
import com.module06.backend.capture.presentation.api.response.SttBlockResponse;
import com.module06.backend.capture.presentation.api.response.SttBlockRetryResponse;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;

/*
 * STT-03 · STT-04 — STT 블록 상태와 재처리.
 *
 * <h2>AnalysisController 와 나눈 이유</h2>
 * 경로 앞부분은 같지만(`/api/meetings/{meetingId}`) 다루는 대상이 다르다. 그쪽은 분석
 * 파이프라인(계층·검토)이고 여기는 **받아쓰기 단계**다 — 회의가 아직 끝나지 않았어도 도는
 * 구간이라 실패의 종류도 사람이 할 일도 다르다. 한 컨트롤러에 몰면 "이 회의가 지금 어디에
 * 있는지"가 파일 하나 안에서 흐려진다.
 *
 * <h2>회사 스코프는 서비스가 본다</h2>
 * stt_block 에는 company_id 가 없어 조회 조건으로 막을 수 없다. {@code @PreAuthorize} 는
 * 역할만 보고 회의 소유를 보지 않으므로, **MeetingAccessGuard 를 지나는 것이 유일한 방어선**이다
 * (CAP-06 이 그 검증을 빠뜨려 뚫려 있던 자리와 같은 성질이다 · #100).
 */
@Tag(name = "Meeting STT", description = "STT 블록 상태·재처리 API")
@RestController
@RequestMapping("/api/meetings/{meetingId}")
@RequiredArgsConstructor
public class SttBlockController {

    private final GetSttBlocksUseCase getSttBlocksUseCase;
    private final RetrySttBlockUseCase retrySttBlockUseCase;

    /*
     * STT-03 · 블록별 처리 상태 조회.
     *
     * 회의가 끝난 뒤 요약이 늦거나 일부 구간이 비었을 때 **어디가 실패했는지 보는 자리**다.
     * cutReason 을 함께 내려주는 이유는 그 다음 질문("왜 그 구간만 이상한가")의 답이 거기 있기
     * 때문이다 — FALLBACK_OVERLAP 은 경계에서 발화가 잘렸을 수 있다.
     */
    @Operation(
            summary = "STT 블록 상태 조회 (STT-03)",
            description = "회의의 10분 단위 STT 블록을 순서대로 준다. status 로 어디가 실패했는지, "
                    + "cutReason 으로 그 블록이 무음에서 잘렸는지(VAD_SILENCE) 겹침 폴백인지"
                    + "(FALLBACK_OVERLAP) 본다 — 폴백 블록은 경계 발화가 잘렸을 수 있다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/stt-blocks")
    public ApiResponse<SttBlockResponse> getSttBlocks(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId
    ) {
        return ApiResponse.success(
                "조회 성공",
                SttBlockResponse.from(getSttBlocksUseCase.getSttBlocks(me.getCompanyId(), meetingId)));
    }

    /*
     * STT-04 · 특정 블록 재처리.
     *
     * **실패한 블록만 다시 돌린다.** 전체 재처리가 아니라 그 블록 하나여서 재과금이 최소화된다 —
     * 10분짜리 블록 하나 때문에 회의 두 시간을 다시 돌리면 요금이 그만큼 다시 나간다.
     *
     * 202 다. 접수만 하고 결과는 STT-03 으로 확인한다.
     */
    @Operation(
            summary = "STT 블록 재처리 (STT-04)",
            description = "실패한 블록 하나를 다시 돌린다. provider 를 주면 다른 제공자로 재시도하고, "
                    + "생략하면 그 블록이 쓰던 제공자를 유지한다. 실패하지 않은 블록은 409 다 — "
                    + "같은 구간에 요금이 두 번 나가고 이미 들어온 발화 위에 덮인다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/stt-blocks/{blockSeq}/retry")
    public ApiResponse<SttBlockRetryResponse> retrySttBlock(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId,
            @PathVariable Integer blockSeq,
            // 본문 전체가 선택이다 — 아무것도 안 보내면 쓰던 제공자를 유지한다.
            @Valid @RequestBody(required = false) SttBlockRetryRequest request
    ) {
        return ApiResponse.accepted(
                "재처리를 시작했습니다.",
                SttBlockRetryResponse.from(retrySttBlockUseCase.retry(new RetrySttBlockCommand(
                        me.getCompanyId(), meetingId, blockSeq,
                        request != null ? request.provider() : null))));
    }
}
