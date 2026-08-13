package com.module06.backend.action.presentation.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.module06.backend.action.application.usecase.GetCompanyMemberActionsUseCase;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.presentation.api.response.ActionSummaryResponse;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.response.PageResponse;

import lombok.RequiredArgsConstructor;

/* comment.
    2026-08-13, 종준님(PO) 확정 — OWNER·ADMIN 전용, 회사 전체 범위에서 특정 구성원의 개인 액션을
    조회하는 진입점. base path = /api/company/actions. 기존 /api/actions(개인, LEADER는 같은 팀
    팀원만)·/api/team/actions(TEAM 액션, 담당자 개념 없음)는 이 작업에서 손대지 않는다 — 이 컨트롤러가
    신규다(종준님 질문 3라운드 최종 확정, member 도메인 기존 파일은 복제·수정 없음).

    assigneeMemberId 파라미터명은 /api/actions의 것과 동일하게 재사용한다(종준님 확정) — 필터
    의미("특정 담당자로 좁힌다")는 같고 스코프(팀 vs 회사) 차이만 경로가 구분한다. 여기서는 회사
    전체가 대상이라 필수값이다(팀장의 "팀원 관리"와 달리, null일 때 "내 액션"으로 대체할 호출자
    본인 개념이 없다 — OWNER·ADMIN이 자기 자신의 액션을 보려는 화면이 아니다).

    companyId는 자기신고 금지, 토큰에서만 꺼낸다(기존 컨트롤러들과 동일 원칙).

    연결된 클래스
    - GetCompanyMemberActionsUseCase : 호출 대상
    - ActionSummaryResponse          : 출력 DTO (GetMyActionsUseCase.ActionListItem 재사용)
    - ApiResponse                    : 성공 응답 래퍼
*/
@RestController
@RequestMapping("/api/company/actions")
@RequiredArgsConstructor
@Tag(name = "Company Action", description = "OWNER·ADMIN 전용 회사 전체 액션 조회 API")
public class CompanyActionController {

    private final GetCompanyMemberActionsUseCase getCompanyMemberActionsUseCase;

    // OWNER·ADMIN(is_admin 겸직 플래그) 허용. Authority enum엔 ADMIN 값이 없어 hasRole로 표현할 수
    // 없다 — BillingController와 동일하게 principal.isAdmin()을 SpEL로 직접 평가한다.
    @Operation(summary = "회사 전체 구성원 액션 목록 조회", description = "OWNER·ADMIN 전용. assigneeMemberId로 "
            + "지정한 구성원의 개인 액션을 회사 전체 범위에서 조회한다. "
            + "페이지네이션(page/size), status·overdue 필터, 정렬(sort=dueDate|createdAt, order=asc|desc).")
    @GetMapping
    @PreAuthorize("hasRole('OWNER') or principal.isAdmin()")
    public ApiResponse<PageResponse<ActionSummaryResponse>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @RequestParam(required = false) Long assigneeMemberId,
            @RequestParam(required = false) ActionStatus status,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = getCompanyMemberActionsUseCase.getCompanyMemberActions(
                companyId, assigneeMemberId, status, overdue, sort, order, page, size);
        List<ActionSummaryResponse> items = result.items().stream()
                .map(ActionSummaryResponse::from)
                .toList();

        return ApiResponse.success("회사 전체 구성원 액션 목록을 조회했습니다.",
                PageResponse.of(items, page, size, result.totalElements()));
    }
}
