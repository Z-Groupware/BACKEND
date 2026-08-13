package com.module06.backend.identity.member.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.member.application.command.DeleteMemberCommand;
import com.module06.backend.identity.member.application.dto.IssuedMember;
import com.module06.backend.identity.member.application.usecase.DeleteMemberUseCase;
import com.module06.backend.identity.member.application.usecase.IssueMemberUseCase;
import com.module06.backend.identity.member.presentation.api.dto.request.IssueMemberRequest;
import com.module06.backend.identity.member.presentation.api.dto.response.IssuedMemberResponse;

import lombok.RequiredArgsConstructor;

@Tag(name = "Identity", description = "계정 발급 API")
@RestController
@RequestMapping("/api/manage/members")
@RequiredArgsConstructor
public class ManageMemberController {

    private final IssueMemberUseCase issueMemberUseCase;
    private final DeleteMemberUseCase deleteMemberUseCase;

    @Operation(summary = "계정 발급", description = "비밀번호를 생성해 기업코드·이메일·비밀번호를 메일로 보냅니다. "
            + "발급 즉시 재직 상태이며, 이 비밀번호는 임시가 아니라 계정의 실제 비밀번호입니다 — "
            + "비밀번호 변경 기능이 없어 분실 시 관리자가 §5-2로 재발급합니다.")
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<IssuedMemberResponse> issue(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Valid @RequestBody IssueMemberRequest request) {
        IssuedMember issued = issueMemberUseCase.issue(request.toCommand(companyId));
        return ApiResponse.success("계정을 발급했습니다. 계정 정보를 메일로 보냈어요.", IssuedMemberResponse.from(issued));
    }

    /**
     * 구성원 관리 화면의 "삭제" 버튼. 계정을 닫을 뿐 행을 지우지 않는다 — 회의 참석자·인수인계
     * 이력이 이 구성원을 참조하고 있어 물리 삭제하면 그 기록이 통째로 끊긴다.
     *
     * <p>응답에 data 가 없다. 삭제된 구성원의 상세를 되돌려 주면 방금 닫은 계정의 이메일·소속이
     * 다시 나가고, 화면도 그 값으로 쓸 데가 없다.
     */
    @Operation(summary = "사원 삭제", description = "구성원을 소프트 삭제합니다(상태 DELETED + deleted_at). "
            + "행을 지우지 않아 회의·인수인계 이력은 남습니다. 오너와 본인은 삭제할 수 없고, 되살리는 경로는 없습니다.")
    @DeleteMapping("/{memberId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long actingMemberId,
            @PathVariable Long memberId) {
        deleteMemberUseCase.delete(new DeleteMemberCommand(companyId, actingMemberId, memberId));
        return ApiResponse.successWithoutData("구성원을 삭제했습니다");
    }
}
