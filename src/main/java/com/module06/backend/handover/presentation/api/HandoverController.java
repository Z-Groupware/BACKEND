package com.module06.backend.handover.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.application.usecase.AttributeHandoverToLeaderUseCase;
import com.module06.backend.handover.application.usecase.CompleteHandoverUseCase;
import com.module06.backend.handover.application.usecase.CreateHandoverUseCase;
import com.module06.backend.handover.application.usecase.FinalizeHandoverUseCase;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.application.usecase.GetPendingAttributionListUseCase;
import com.module06.backend.handover.application.usecase.ReassignHandoverItemUseCase;
import com.module06.backend.handover.application.usecase.RejectHandoverUseCase;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.presentation.api.dto.request.AttributeToLeaderRequest;
import com.module06.backend.handover.presentation.api.dto.request.CreateHandoverRequest;
import com.module06.backend.handover.presentation.api.dto.request.ReassignItemRequest;
import com.module06.backend.handover.presentation.api.dto.request.RejectHandoverRequest;
import com.module06.backend.handover.presentation.api.dto.response.HandoverResponse;
import com.module06.backend.handover.presentation.api.dto.response.HandoverSummaryResponse;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/handovers")
public class HandoverController {

    private final CreateHandoverUseCase createHandoverUseCase;
    private final ReassignHandoverItemUseCase reassignHandoverItemUseCase;
    private final CompleteHandoverUseCase completeHandoverUseCase;
    private final FinalizeHandoverUseCase finalizeHandoverUseCase;
    private final RejectHandoverUseCase rejectHandoverUseCase;
    private final GetHandoverListUseCase getHandoverListUseCase;
    private final GetPendingAttributionListUseCase getPendingAttributionListUseCase;
    private final AttributeHandoverToLeaderUseCase attributeHandoverToLeaderUseCase;
    private final OrgQueryPort orgQueryPort;

    public HandoverController(CreateHandoverUseCase createHandoverUseCase,
                              ReassignHandoverItemUseCase reassignHandoverItemUseCase,
                              CompleteHandoverUseCase completeHandoverUseCase,
                              FinalizeHandoverUseCase finalizeHandoverUseCase,
                              RejectHandoverUseCase rejectHandoverUseCase,
                              GetHandoverListUseCase getHandoverListUseCase,
                              GetPendingAttributionListUseCase getPendingAttributionListUseCase,
                              AttributeHandoverToLeaderUseCase attributeHandoverToLeaderUseCase,
                              OrgQueryPort orgQueryPort) {
        this.createHandoverUseCase = createHandoverUseCase;
        this.reassignHandoverItemUseCase = reassignHandoverItemUseCase;
        this.completeHandoverUseCase = completeHandoverUseCase;
        this.finalizeHandoverUseCase = finalizeHandoverUseCase;
        this.rejectHandoverUseCase = rejectHandoverUseCase;
        this.getHandoverListUseCase = getHandoverListUseCase;
        this.getPendingAttributionListUseCase = getPendingAttributionListUseCase;
        this.attributeHandoverToLeaderUseCase = attributeHandoverToLeaderUseCase;
        this.orgQueryPort = orgQueryPort;
    }

    @GetMapping
    public ApiResponse<List<HandoverSummaryResponse>> list(
            @RequestParam(required = false) Long writerMemberId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) HandoverStatus status) {
        // TODO: auth(B) 도입 후 writerMemberId/teamId를 JWT 스코프로 대체·검증.
        List<HandoverSummaryResponse> data = getHandoverListUseCase
                .list(new GetHandoverListUseCase.HandoverListQuery(writerMemberId, teamId, status)).stream()
                .map(HandoverSummaryResponse::from)
                .toList();
        return ApiResponse.success("인수인계 목록을 조회했습니다.", data);
    }

    @GetMapping("/pending-attribution")
    public ApiResponse<List<HandoverSummaryResponse>> pendingAttribution(@RequestParam Long companyId) {
        // TODO: auth(B) 도입 후 companyId를 JWT claim으로 대체한다.
        List<HandoverSummaryResponse> data = getPendingAttributionListUseCase
                .listPendingAttribution(companyId).stream()
                .map(HandoverSummaryResponse::from)
                .toList();
        return ApiResponse.success("Pending attribution handovers retrieved.", data);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HandoverResponse> create(@Valid @RequestBody CreateHandoverRequest request) {
        Handover handover = createHandoverUseCase.create(request.toCommand());
        return ApiResponse.created("Handover created.", HandoverResponse.from(handover));
    }

    @PatchMapping("/{id}/items/{actionId}/reassign")
    public ApiResponse<HandoverResponse> reassignItem(@PathVariable Long id,
                                                      @PathVariable Long actionId,
                                                      @Valid @RequestBody ReassignItemRequest request) {
        Handover handover = reassignHandoverItemUseCase.reassignItem(
                request.toCommand(id, actionId, LocalDateTime.now())
        );
        return ApiResponse.success("Handover item reassigned.", HandoverResponse.from(handover));
    }

    /*
     * 승인자를 토큰에서 꺼낸다. 헤더로 받으면 남의 사번을 적어 그 사람 이름으로 승인할 수 있고,
     * 승인은 감사 기록이 남는 행위라(finalApproverNameSnap) 신분 위조가 곧 기록 위조가 된다.
     */
    @PatchMapping("/{id}/complete")
    public ApiResponse<HandoverResponse> complete(@PathVariable Long id,
                                                  @Parameter(hidden = true)
                                                  @AuthenticationPrincipal(expression = "memberId") Long memberId) {
        Handover handover = completeHandoverUseCase.complete(id, memberId, LocalDateTime.now());
        return ApiResponse.success("Handover completed.", HandoverResponse.from(handover));
    }

    @PatchMapping("/{id}/finalize")
    public ApiResponse<HandoverResponse> finalize(@PathVariable Long id,
                                                  @Parameter(hidden = true)
                                                  @AuthenticationPrincipal(expression = "memberId") Long memberId) {
        OrgQueryPort.MemberSnapshot approver = orgQueryPort.findMember(memberId);
        Handover handover = finalizeHandoverUseCase.finalize(id, memberId, approver.name(), LocalDateTime.now());
        return ApiResponse.success("Handover finalized.", HandoverResponse.from(handover));
    }

    @PatchMapping("/{id}/attribute-to-leader")
    public ApiResponse<HandoverResponse> attributeToLeader(@PathVariable Long id,
                                                           @Valid @RequestBody AttributeToLeaderRequest request,
                                                           @Parameter(hidden = true)
                                                           @AuthenticationPrincipal(expression = "memberId")
                                                           Long memberId) {
        Handover handover = attributeHandoverToLeaderUseCase.attributeToNewLeader(
                request.toCommand(id, memberId, LocalDateTime.now())
        );
        return ApiResponse.success("Handover attributed to leader.", HandoverResponse.from(handover));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<HandoverResponse> reject(@PathVariable Long id,
                                                @Valid @RequestBody RejectHandoverRequest request) {
        Handover handover = rejectHandoverUseCase.reject(request.toCommand(id));
        return ApiResponse.success("Handover rejected.", HandoverResponse.from(handover));
    }
}
