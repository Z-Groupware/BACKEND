package com.module06.backend.handover.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.application.usecase.CompleteHandoverUseCase;
import com.module06.backend.handover.application.usecase.CreateHandoverUseCase;
import com.module06.backend.handover.application.usecase.FinalizeHandoverUseCase;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.application.usecase.ReassignHandoverItemUseCase;
import com.module06.backend.handover.application.usecase.RejectHandoverUseCase;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.presentation.api.dto.request.CreateHandoverRequest;
import com.module06.backend.handover.presentation.api.dto.request.ReassignItemRequest;
import com.module06.backend.handover.presentation.api.dto.request.RejectHandoverRequest;
import com.module06.backend.handover.presentation.api.dto.response.HandoverResponse;
import com.module06.backend.handover.presentation.api.dto.response.HandoverSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final OrgQueryPort orgQueryPort;

    public HandoverController(CreateHandoverUseCase createHandoverUseCase,
                              ReassignHandoverItemUseCase reassignHandoverItemUseCase,
                              CompleteHandoverUseCase completeHandoverUseCase,
                              FinalizeHandoverUseCase finalizeHandoverUseCase,
                              RejectHandoverUseCase rejectHandoverUseCase,
                              GetHandoverListUseCase getHandoverListUseCase,
                              OrgQueryPort orgQueryPort) {
        this.createHandoverUseCase = createHandoverUseCase;
        this.reassignHandoverItemUseCase = reassignHandoverItemUseCase;
        this.completeHandoverUseCase = completeHandoverUseCase;
        this.finalizeHandoverUseCase = finalizeHandoverUseCase;
        this.rejectHandoverUseCase = rejectHandoverUseCase;
        this.getHandoverListUseCase = getHandoverListUseCase;
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

    @PatchMapping("/{id}/complete")
    public ApiResponse<HandoverResponse> complete(@PathVariable Long id,
                                                  @RequestHeader("X-Member-Id") Long memberId) {
        // TEMP: B(auth) 배선 전까지 헤더 브리지. SecurityContext로 교체 예정.
        Handover handover = completeHandoverUseCase.complete(id, memberId, LocalDateTime.now());
        return ApiResponse.success("Handover completed.", HandoverResponse.from(handover));
    }

    @PatchMapping("/{id}/finalize")
    public ApiResponse<HandoverResponse> finalize(@PathVariable Long id,
                                                  @RequestHeader("X-Member-Id") Long memberId) {
        // TEMP: B(auth) 배선 전까지 헤더 브리지. SecurityContext로 교체 예정.
        OrgQueryPort.MemberSnapshot approver = orgQueryPort.findMember(memberId);
        Handover handover = finalizeHandoverUseCase.finalize(id, memberId, approver.name(), LocalDateTime.now());
        return ApiResponse.success("Handover finalized.", HandoverResponse.from(handover));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<HandoverResponse> reject(@PathVariable Long id,
                                                @Valid @RequestBody RejectHandoverRequest request) {
        Handover handover = rejectHandoverUseCase.reject(request.toCommand(id));
        return ApiResponse.success("Handover rejected.", HandoverResponse.from(handover));
    }
}
