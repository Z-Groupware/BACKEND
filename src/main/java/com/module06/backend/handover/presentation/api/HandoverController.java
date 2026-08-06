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
import com.module06.backend.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /*
     * 회사 경계는 토큰의 companyId 로만 세운다. writerMemberId/teamId 는 "회사 안에서 무엇을
     * 볼지" 고르는 필터일 뿐이고, 남의 회사 teamId·사번을 넣어도 서비스가 companyId 로 걸러낸다
     * (사번 월경은 403, 팀 월경은 빈 목록). 이걸 본문/쿼리로 받으면 그 관문이 무너진다.
     */
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping
    public ApiResponse<List<HandoverSummaryResponse>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(required = false) Long writerMemberId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) HandoverStatus status) {
        List<HandoverSummaryResponse> data = getHandoverListUseCase
                .list(new GetHandoverListUseCase.HandoverListQuery(me.getCompanyId(), writerMemberId, teamId, status)).stream()
                .map(HandoverSummaryResponse::from)
                .toList();
        return ApiResponse.success("인수인계 목록을 조회했습니다.", data);
    }

    /*
     * 작성자·팀을 토큰에서 꺼낸다(complete/finalize 와 같은 이유). 본문으로 받으면 로그인만
     * 하면 남의 사번을 적어 그 사람 이름으로 인계서를 낼 수 있고, 인계서는 writerNameSnap 이
     * 박히는 기록물이라 신분 위조가 곧 기록 위조가 된다.
     */
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HandoverResponse> create(@Parameter(hidden = true)
                                                @AuthenticationPrincipal AuthPrincipal me,
                                                @Valid @RequestBody CreateHandoverRequest request) {
        Handover handover = createHandoverUseCase.create(request.toCommand(me.getMemberId(), me.getTeamId()));
        return ApiResponse.created("Handover created.", HandoverResponse.from(handover));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
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
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER')")
    @PatchMapping("/{id}/complete")
    public ApiResponse<HandoverResponse> complete(@PathVariable Long id,
                                                  @Parameter(hidden = true)
                                                  @AuthenticationPrincipal(expression = "memberId") Long memberId) {
        Handover handover = completeHandoverUseCase.complete(id, memberId, LocalDateTime.now());
        return ApiResponse.success("Handover completed.", HandoverResponse.from(handover));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER')")
    @PatchMapping("/{id}/finalize")
    public ApiResponse<HandoverResponse> finalize(@PathVariable Long id,
                                                  @Parameter(hidden = true)
                                                  @AuthenticationPrincipal(expression = "memberId") Long memberId) {
        OrgQueryPort.MemberSnapshot approver = orgQueryPort.findMember(memberId);
        Handover handover = finalizeHandoverUseCase.finalize(id, memberId, approver.name(), LocalDateTime.now());
        return ApiResponse.success("Handover finalized.", HandoverResponse.from(handover));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER')")
    @PatchMapping("/{id}/reject")
    public ApiResponse<HandoverResponse> reject(@PathVariable Long id,
                                                @Valid @RequestBody RejectHandoverRequest request) {
        Handover handover = rejectHandoverUseCase.reject(request.toCommand(id));
        return ApiResponse.success("Handover rejected.", HandoverResponse.from(handover));
    }
}
