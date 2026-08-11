package com.module06.backend.action.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.module06.backend.action.application.command.BulkUpdateActionStatusCommand;
import com.module06.backend.action.application.command.CreateActionCommand;
import com.module06.backend.action.application.usecase.BulkUpdateActionStatusUseCase;
import com.module06.backend.action.application.usecase.CreateActionUseCase;
import com.module06.backend.action.application.usecase.GetActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.presentation.api.request.BulkUpdateActionStatusRequest;
import com.module06.backend.action.presentation.api.request.CreateActionRequest;
import com.module06.backend.action.presentation.api.response.ActionDetailResponse;
import com.module06.backend.action.presentation.api.response.ActionSummaryResponse;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.response.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/* comment.
    FR-AC-01,02,03 — 개인 액션 API 진입점. 체크리스트(FR-AC-05)는 2026-08-08 PO 확인으로
    스코프 아웃 확정 — 캘린더·개인 Todo가 그 역할을 대체한다(스켈레톤은 삭제 대기 중).
    회의별 조회(FR-AC-09)는 리소스가 달라 MeetingActionController(/api/meetings)로 분리했다.

    담당 엔드포인트
    - POST   /api/actions                                수동 추가 (예외 경로, MEMBER+)
    - GET    /api/actions                                내 액션 목록 조회 (기본 호출자 본인, LEADER는
                                                            assigneeMemberId로 같은 팀 팀원 조회 가능)
    - GET    /api/actions/{actionId}                     상세 조회 (전 구성원)
    - PATCH  /api/actions/complete/bulk                   보드 저장 시 일괄 완료/완료취소 (담당자 본인)
    응답은 ApiResponse, 예외는 BusinessException으로만 낸다 — 개별 try-catch 금지(0절 4항).

    FR-AC-03은 벌크 엔드포인트만 둔다(2026-08-07 결정) — Figma 확인 결과 개인 액션 상세는
    "조회 전용"이고 상태변경은 보드(칸반+저장) 화면에서만 일어난다. 단건 PATCH는 화면 근거가
    없어 만들지 않는다(UpdateActionStatusUseCase·Command·Request도 함께 삭제).

    URL을 status/bulk → complete/bulk로 개명(2026-08-09) — status가 isDone 파생값이 되면서
    실제로는 완료/완료취소 토글만 하는 API가 됐는데 이름이 그대로였다. 홍근 확인(연동 전이라
    자유롭게 바꿔도 된다는 회신) 받고 진행. 클래스·메서드·DTO 이름(BulkUpdateActionStatus*)은
    안 바꿈 — URL(계약)만 실제 동작과 맞춘 것, 내부 구현은 여전히 상태 파생 로직이라 그대로 둔다.
    FR-AC-04(리뷰확정)는 C에 없다 — review(A)의 RVW-02가 자체 엔드포인트로 이미 처리하고,
    C는 ActionReviewApplyPort(A가 선언)를 어댑터로 받기만 한다(ActionService 주석 참고).

    연결된 클래스
    - CreateActionUseCase · GetMyActionsUseCase · GetActionDetailUseCase ·
      BulkUpdateActionStatusUseCase                                                    : 호출 대상
    - CreateActionRequest · BulkUpdateActionStatusRequest                              : 입력 DTO
    - ActionSummaryResponse · ActionDetailResponse                                     : 출력 DTO
    - ApiResponse                                                                      : 성공 응답 래퍼
*/
@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
@Tag(name = "Action", description = "액션 API")
public class ActionController {

    private final CreateActionUseCase createActionUseCase;
    private final GetMyActionsUseCase getMyActionsUseCase;
    private final GetActionDetailUseCase getActionDetailUseCase;
    private final BulkUpdateActionStatusUseCase bulkUpdateActionStatusUseCase;

    @Operation(summary = "액션 수동 추가", description = "AI 분배를 놓친 액션을 사람이 직접 추가하는 예외 경로.")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ActionSummaryResponse> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Valid @RequestBody CreateActionRequest request
    ) {
        Action action = createActionUseCase.create(new CreateActionCommand(
                companyId,
                request.projectId(),
                request.actionType(),
                request.teamId(),
                request.assigneeMemberId(),
                request.title(),
                request.description(),
                request.dueDate()
        ));

        return ApiResponse.created("액션을 추가했습니다.", ActionSummaryResponse.from(action));
    }

    // 내 액션 목록 — 호출자 memberId·authority·teamId는 전부 토큰에서만 꺼낸다(헤더로 받으면
    // 남의 목록을 조회하거나 권한을 자기신고할 수 있다).
    // 2026-08-10 페이지네이션+필터+정렬 도입(이홍근 요청) — page 0부터 시작, size 기본 20.
    // 2026-08-11 — assigneeMemberId(선택) 추가(이홍근 요청, 팀원 관리 화면). 값이 있으면
    // 팀장이 그 팀원의 목록을 대신 조회하는 것으로 전환 — LEADER 자격·같은 팀 소속 검증은
    // ActionService가 한다(IDOR 방지, companyId처럼 클라이언트 자기신고 값이 아니라
    // 매번 토큰의 teamId와 대조).
    @Operation(summary = "내 액션 목록 조회", description = "기본은 호출자 본인 소유 개인 액션. "
            + "assigneeMemberId를 주면 그 팀원의 목록을 대신 조회한다(LEADER 전용, 같은 팀 소속만). "
            + "페이지네이션(page/size), status 필터, overdue(지연) 필터, 정렬(sort=dueDate|createdAt, order=asc|desc).")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<ActionSummaryResponse>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "authority") String authority,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "teamId") Long teamId,
            @RequestParam(required = false) Long assigneeMemberId,
            @RequestParam(required = false) ActionStatus status,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = getMyActionsUseCase.getMyActions(
                memberId, authority, teamId, assigneeMemberId, status, overdue, sort, order, page, size);
        List<ActionSummaryResponse> items = result.items().stream()
                .map(ActionSummaryResponse::from)
                .toList();

        return ApiResponse.success("내 액션 목록을 조회했습니다.",
                PageResponse.of(items, page, size, result.totalElements()));
    }

    // 액션 상세 — 전 구성원 공개, companyId만 토큰에서 확인한다(IDOR 방지).
    @Operation(summary = "액션 상세 조회", description = "전 구성원 공개 열람.")
    @GetMapping("/{actionId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ActionDetailResponse> detail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long actionId
    ) {
        ActionDetailResponse response = ActionDetailResponse.from(
                getActionDetailUseCase.getActionDetail(companyId, actionId));

        return ApiResponse.success("액션 상세를 조회했습니다.", response);
    }

    // 보드 "저장" 버튼 — 담당자 본인 검사는 항목별로 서비스가 한다. requesterId도 토큰에서만 꺼낸다.
    @Operation(summary = "액션 상태 일괄 변경", description = "보드 \"저장\" 버튼 — 담당자 본인 소유 액션만 대상, all-or-nothing.")
    @PatchMapping("/complete/bulk")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> bulkUpdateStatus(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody BulkUpdateActionStatusRequest request
    ) {
        List<BulkUpdateActionStatusCommand.Item> items = request.items().stream()
                .map(item -> new BulkUpdateActionStatusCommand.Item(item.actionId(), item.status()))
                .toList();

        bulkUpdateActionStatusUseCase.bulkUpdateStatus(new BulkUpdateActionStatusCommand(memberId, items));

        return ApiResponse.successWithoutData("액션 상태를 일괄 변경했습니다.");
    }
}
