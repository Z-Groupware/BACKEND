package com.module06.backend.handover.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.handover.application.usecase.CompleteHandoverUseCase;
import com.module06.backend.handover.application.usecase.CreateHandoverUseCase;
import com.module06.backend.handover.application.usecase.FinalizeHandoverUseCase;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase;
import com.module06.backend.handover.application.usecase.HandoverToSuccessorUseCase;
import com.module06.backend.handover.application.usecase.ReassignHandoverItemUseCase;
import com.module06.backend.handover.application.usecase.RejectHandoverUseCase;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.presentation.api.request.CompleteHandoverRequest;
import com.module06.backend.handover.presentation.api.request.CreateHandoverRequest;
import com.module06.backend.handover.presentation.api.request.FinalizeHandoverRequest;
import com.module06.backend.handover.presentation.api.request.HandoverToSuccessorRequest;
import com.module06.backend.handover.presentation.api.request.ReassignItemRequest;
import com.module06.backend.handover.presentation.api.request.RejectHandoverRequest;
import com.module06.backend.handover.presentation.api.response.HandoverPackageResponse;
import com.module06.backend.handover.presentation.api.response.HandoverResponse;
import com.module06.backend.handover.presentation.api.response.HandoverSummaryResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
    FR-HO-01,02,03,04,05,06 — 인수인계 REST API 진입점. base path = /api/handovers.
    담당 엔드포인트
    - POST  /api/handovers                                      인수인계 생성
    - PATCH /api/handovers/{handoverId}/items/{actionId}/reassign 항목 재배정
    - POST  /api/handovers/{handoverId}/complete                 리더 인수인계 완료
    - POST  /api/handovers/{handoverId}/finalize                 최종 승인
    - POST  /api/handovers/{handoverId}/reject                   반려
    - GET   /api/handovers/{handoverId}                          인계서 패키지 조회
    - GET   /api/handovers?writerMemberId=&teamId=&status=       관리 목록 조회(사원 본인/팀장 팀 스코프)
    - POST  /api/handovers/{handoverId}/handover-to-successor    오너 일괄 이관+최종승인(팀장급)
    응답은 ApiResponse, 예외는 BusinessException으로만 낸다 — 개별 try-catch 금지.

    연결된 클래스
    - CreateHandoverUseCase · ReassignHandoverItemUseCase · CompleteHandoverUseCase ·
      FinalizeHandoverUseCase · RejectHandoverUseCase · GetHandoverPackageUseCase : 호출 대상
    - CreateHandoverRequest · ReassignItemRequest · CompleteHandoverRequest ·
      FinalizeHandoverRequest · RejectHandoverRequest                         : 입력 DTO
    - HandoverResponse · HandoverPackageResponse                               : 출력 DTO
    - ApiResponse                                                              : 성공 응답 래퍼
*/
@RestController
@RequestMapping("/api/handovers")
public class HandoverController {

    private final CreateHandoverUseCase createHandoverUseCase;
    private final ReassignHandoverItemUseCase reassignHandoverItemUseCase;
    private final CompleteHandoverUseCase completeHandoverUseCase;
    private final FinalizeHandoverUseCase finalizeHandoverUseCase;
    private final RejectHandoverUseCase rejectHandoverUseCase;
    private final GetHandoverPackageUseCase getHandoverPackageUseCase;
    private final GetHandoverListUseCase getHandoverListUseCase;
    private final HandoverToSuccessorUseCase handoverToSuccessorUseCase;

    public HandoverController(CreateHandoverUseCase createHandoverUseCase,
                              ReassignHandoverItemUseCase reassignHandoverItemUseCase,
                              CompleteHandoverUseCase completeHandoverUseCase,
                              FinalizeHandoverUseCase finalizeHandoverUseCase,
                              RejectHandoverUseCase rejectHandoverUseCase,
                              GetHandoverPackageUseCase getHandoverPackageUseCase,
                              GetHandoverListUseCase getHandoverListUseCase,
                              HandoverToSuccessorUseCase handoverToSuccessorUseCase) {
        this.createHandoverUseCase = createHandoverUseCase;
        this.reassignHandoverItemUseCase = reassignHandoverItemUseCase;
        this.completeHandoverUseCase = completeHandoverUseCase;
        this.finalizeHandoverUseCase = finalizeHandoverUseCase;
        this.rejectHandoverUseCase = rejectHandoverUseCase;
        this.getHandoverPackageUseCase = getHandoverPackageUseCase;
        this.getHandoverListUseCase = getHandoverListUseCase;
        this.handoverToSuccessorUseCase = handoverToSuccessorUseCase;
    }

    @GetMapping
    public ApiResponse<List<HandoverSummaryResponse>> list(
            @RequestParam(required = false) Long writerMemberId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) HandoverStatus status) {
        // TODO: auth(B) 도입 후 writerMemberId/teamId를 JWT 스코프로 대체·검증.
        List<HandoverSummaryResponse> response = getHandoverListUseCase.list(
                        new GetHandoverListUseCase.HandoverListQuery(writerMemberId, teamId, status)).stream()
                .map(HandoverSummaryResponse::from)
                .toList();
        return ApiResponse.success("인수인계 목록을 조회했습니다.", response);
    }

    @PostMapping("/{handoverId}/handover-to-successor")
    public ApiResponse<HandoverResponse> handoverToSuccessor(
            @PathVariable Long handoverId,
            @Valid @RequestBody HandoverToSuccessorRequest request) {
        // TODO: auth(B) 도입 후 owner 권한 검증.
        HandoverResponse response = HandoverResponse.from(handoverToSuccessorUseCase.handoverToSuccessor(
                handoverId, request.successorId(), request.ownerId(), request.ownerName(), LocalDateTime.now()));
        return ApiResponse.success("인계 액션을 후임에게 일괄 이관하고 최종 승인했습니다.", response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HandoverResponse>> create(@Valid @RequestBody CreateHandoverRequest request) {
        // TODO: auth(B) 도입 후 writerMemberId 권한 검증.
        HandoverResponse response = HandoverResponse.from(createHandoverUseCase.create(request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("인수인계가 생성되었습니다.", response));
    }

    @PatchMapping("/{handoverId}/items/{actionId}/reassign")
    public ApiResponse<HandoverResponse> reassignItem(@PathVariable Long handoverId,
                                                      @PathVariable Long actionId,
                                                      @Valid @RequestBody ReassignItemRequest request) {
        // TODO: auth(B) 도입 후 leader 권한 검증.
        HandoverResponse response = HandoverResponse.from(reassignHandoverItemUseCase.reassignItem(
                request.toCommand(handoverId, actionId, LocalDateTime.now())));
        return ApiResponse.success("인수인계 항목이 재배정되었습니다.", response);
    }

    @PostMapping("/{handoverId}/complete")
    public ApiResponse<HandoverResponse> complete(@PathVariable Long handoverId,
                                                  @Valid @RequestBody CompleteHandoverRequest request) {
        // TODO: auth(B) 도입 후 leader 권한 검증.
        HandoverResponse response = HandoverResponse.from(completeHandoverUseCase.complete(
                handoverId, request.leaderId(), LocalDateTime.now()));
        return ApiResponse.success("인수인계가 완료되었습니다.", response);
    }

    @PostMapping("/{handoverId}/finalize")
    public ApiResponse<HandoverResponse> finalize(@PathVariable Long handoverId,
                                                  @Valid @RequestBody FinalizeHandoverRequest request) {
        // TODO: auth(B) 도입 후 approver 권한 검증.
        HandoverResponse response = HandoverResponse.from(finalizeHandoverUseCase.finalize(
                handoverId, request.approverId(), request.approverName(), LocalDateTime.now()));
        return ApiResponse.success("인수인계가 최종 승인되었습니다.", response);
    }

    @PostMapping("/{handoverId}/reject")
    public ApiResponse<HandoverResponse> reject(@PathVariable Long handoverId,
                                                @Valid @RequestBody RejectHandoverRequest request) {
        // TODO: auth(B) 도입 후 approver 권한 검증.
        HandoverResponse response = HandoverResponse.from(rejectHandoverUseCase.reject(request.toCommand(handoverId)));
        return ApiResponse.success("인수인계가 반려되었습니다.", response);
    }

    @GetMapping("/{handoverId}")
    public ApiResponse<HandoverPackageResponse> getPackage(
            @PathVariable Long handoverId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        LocalDate effectiveReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        HandoverPackageResponse response = HandoverPackageResponse.from(
                getHandoverPackageUseCase.getPackage(handoverId, effectiveReferenceDate));
        return ApiResponse.success("인수인계 패키지를 조회했습니다.", response);
    }
}
