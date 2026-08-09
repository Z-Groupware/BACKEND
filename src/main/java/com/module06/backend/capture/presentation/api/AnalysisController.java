package com.module06.backend.capture.presentation.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.usecase.AddReviewActionUseCase;
import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase;
import com.module06.backend.capture.application.usecase.ConfirmDistributionUseCase;
import com.module06.backend.capture.application.usecase.ConfirmDistributionUseCase.ConfirmDistributionCommand;
import com.module06.backend.capture.application.usecase.CancelReviewActionUseCase;
import com.module06.backend.capture.application.usecase.CancelReviewActionUseCase.CancelReviewActionCommand;
import com.module06.backend.capture.application.usecase.GetActionReviewUseCase;
import com.module06.backend.capture.application.usecase.GetProcessingStatusUseCase;
import com.module06.backend.capture.application.usecase.GetSummaryUseCase;
import com.module06.backend.capture.application.usecase.RunAnalysisUseCase;
import com.module06.backend.capture.presentation.api.request.AddReviewActionRequest;
import com.module06.backend.capture.presentation.api.request.ReviewDecisionRequest;
import com.module06.backend.capture.presentation.api.response.ActionReviewResponse;
import com.module06.backend.capture.presentation.api.response.AddReviewActionResponse;
import com.module06.backend.capture.presentation.api.response.AnalysisRunResponse;
import com.module06.backend.capture.presentation.api.response.DistributionConfirmResponse;
import com.module06.backend.capture.presentation.api.response.MeetingSummaryResponse;
import com.module06.backend.capture.presentation.api.response.ProcessingStatusResponse;
import com.module06.backend.capture.presentation.api.response.ReviewDecisionResponse;
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
    private final GetActionReviewUseCase getActionReviewUseCase;
    private final ApplyReviewDecisionUseCase applyReviewDecisionUseCase;
    private final ConfirmDistributionUseCase confirmDistributionUseCase;
    private final CancelReviewActionUseCase cancelReviewActionUseCase;
    private final AddReviewActionUseCase addReviewActionUseCase;

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

    /*
     * RVW-01 · 액션 분배 검토 조회.
     *
     * 파이프라인 산출물을 **사람이 처음 보는 자리**다. L7 이 가른 두 묶음(「AI 확신도 높음」 ·
     * 「AI 확인 필요」)이 여기서 화면으로 나간다.
     *
     * ⚠ 분배(RVW-05)가 아직 없어 dispatchedAt 은 항상 null 이다. 자동 확정 건도 분배
     * 전까지는 아무 데도 가 있지 않다는 뜻이 그 값으로 드러난다.
     */
    @Operation(
            summary = "액션 분배 검토 조회 (RVW-01)",
            description = "회의에서 도출된 액션을 담당자별로 묶어 조회한다. "
                    + "게이트 신호와 근거 발화를 함께 내려주며, 모델이 말한 확신도 숫자는 없다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/review")
    public ApiResponse<ActionReviewResponse> getReview(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId,
            @Parameter(description = "PENDING 으로 검토 대상만 필터. 생략하면 전체")
            @RequestParam(required = false) String reviewStatus
    ) {
        return ApiResponse.success(
                "조회 성공",
                ActionReviewResponse.from(
                        getActionReviewUseCase.getReview(me.getCompanyId(), meetingId, reviewStatus)));
    }

    /*
     * RVW-02 · 액션 항목 수정·반려.
     *
     * **사람이 확정하는 이 순간이 시스템에서 유일한 쓰기 지점이다**(명세 RVW-02). 화면에서는
     * "담당자 바꾸고 저장"이지만, 이 저장소가 여기서 얻는 것은 {AI 입력 → 사람이 인정한 정답}
     * 한 쌍이다 — 특화 모델의 유일한 연료이고 지나간 회의는 다시 만들 수 없다.
     *
     * MEMBER 까지 허용한다. 검토는 회의 참석자가 하는 일이고, 역할로 막으면 사원이 참석한
     * 회의의 자기 액션도 못 고친다. 회사 스코프는 MeetingAccessGuard 가 본다.
     */
    @Operation(
            summary = "액션 항목 수정·반려 (RVW-02)",
            description = "검토 화면에서 담당자·기한을 고치거나 반려한다. 판정은 review_log 에 "
                    + "라벨로 남고, 확정·수정 건은 few-shot 예시로 예약된다. "
                    + "수정·반려에는 사유 코드가 필수다(422)."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PatchMapping("/review/{actionId}")
    public ApiResponse<ReviewDecisionResponse> applyReviewDecision(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId,
            @PathVariable Long actionId,
            @Valid @RequestBody ReviewDecisionRequest request
    ) {
        return ApiResponse.success(
                "반영되었습니다.",
                ReviewDecisionResponse.from(applyReviewDecisionUseCase.apply(
                        request.toCommand(me.getCompanyId(), meetingId, actionId, me.getMemberId()))));
    }

    /*
     * RVW-05 · 액션 분배 확정.
     *
     * **파이프라인의 마지막 사람 손이다.** 이 호출 전까지 액션은 만들어져 있어도 아무 데도 가
     * 있지 않고, 이 호출 뒤에 각자의 보드에 카드가 생긴다 — 그때부터는 C(액션·보드) 소관이다.
     *
     * 회의 담당자만 부를 수 있다(403). 검토는 참석자 누구나 하되 **마지막 버튼은 한 사람**이다 —
     * 분배는 되돌릴 수 없기 때문이다. 그 판정은 서비스가 한다(역할이 아니라 그 회의의 host 다).
     */
    @Operation(
            summary = "액션 분배 확정 (RVW-05)",
            description = "검토를 끝내고 액션을 담당자 보드로 내보낸다. 확인되지 않은 STT 구간이나 "
                    + "미검토 액션이 남아 있으면 409 이며 confirm=true 로만 강행한다. "
                    + "강행해도 미검토·반려·담당자 미정 액션은 나가지 않고 skipped 로 돌아온다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/review/confirm")
    public ApiResponse<DistributionConfirmResponse> confirmDistribution(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId,
            @Parameter(description = "true 면 미검토·미확인 구멍이 남아 있어도 강행한다")
            @RequestParam(defaultValue = "false") boolean confirm
    ) {
        return ApiResponse.success(
                "분배가 확정되었습니다.",
                DistributionConfirmResponse.from(confirmDistributionUseCase.confirm(
                        new ConfirmDistributionCommand(
                                me.getCompanyId(), meetingId, me.getMemberId(), confirm))));
    }
    /*
     * RVW-04 · 직접 추가한 액션 취소.
     *
     * **지울 수 있는 것은 사람이 직접 넣은 액션뿐이다.** AI 가 만든 액션은 반려(RVW-02)로
     * 처리한다 — 지우면 「AI 가 이런 걸 뽑았고 사람이 아니라고 했다」는 라벨이 사라지고,
     * 지나간 회의는 다시 만들 수 없어 되돌릴 수도 없다.
     */
    @Operation(
            summary = "직접 추가한 액션 취소 (RVW-04)",
            description = "검토 화면에서 직접 추가한 액션을 지운다. isManual=true 인 액션만 "
                    + "지울 수 있고, AI 생성 액션은 409 로 막힌다 — 그쪽은 RVW-02 의 REJECT 다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @DeleteMapping("/review/actions/{actionId}")
    public ApiResponse<Void> cancelReviewAction(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId,
            @PathVariable Long actionId
    ) {
        cancelReviewActionUseCase.cancel(new CancelReviewActionCommand(
                me.getCompanyId(), meetingId, actionId, me.getMemberId()));

        return ApiResponse.success("삭제되었습니다.", null);
    }
    /*
     * RVW-03 · 액션 직접 추가.
     *
     * **AI 가 놓친 것을 사람이 넣는 자리다.** 파이프라인이 못 뽑은 할 일은 검토 화면에 아예
     * 나타나지 않으므로, 이 경로가 없으면 사람은 빠진 것을 알아도 넣을 방법이 없다.
     *
     * MEMBER 까지 허용한다 — 검토는 회의 참석자가 하는 일이고, RVW-02 와 같은 판단이다.
     */
    @Operation(
            summary = "액션 직접 추가 (RVW-03)",
            description = "검토 화면에서 사람이 액션을 직접 추가한다. 만들어지는 액션은 "
                    + "isManual=true · HUMAN_CONFIRMED 이며, AI 입력이 없어 few-shot 예시로는 "
                    + "쓰이지 않는다. 담당자와 기한은 필수다(422)."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/review/actions")
    public ApiResponse<AddReviewActionResponse> addReviewAction(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long meetingId,
            @Valid @RequestBody AddReviewActionRequest request
    ) {
        return ApiResponse.success(
                "추가되었습니다.",
                AddReviewActionResponse.from(addReviewActionUseCase.add(
                        request.toCommand(me.getCompanyId(), meetingId, me.getMemberId()))));
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
