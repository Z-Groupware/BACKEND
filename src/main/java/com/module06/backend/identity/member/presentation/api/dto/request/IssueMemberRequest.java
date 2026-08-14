package com.module06.backend.identity.member.presentation.api.dto.request;

import com.module06.backend.identity.member.application.command.IssueMemberCommand;
import com.module06.backend.identity.member.domain.model.Authority;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 계정 발급(§5-1). {@code isAdmin} 은 없다 — 발급 시점엔 항상 false 다. */
@Schema(description = "계정 발급")
public record IssueMemberRequest(

        @Schema(description = "이름", example = "홍길동")
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 50)
        String name,

        @Schema(description = "이메일 — 로그인 아이디가 된다", example = "name@company.kr")
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255)
        String email,

        @Schema(description = "부서 id", example = "2")
        @NotNull(message = "부서를 선택해 주세요.")
        Long teamId,

        @Schema(description = "직급 id", example = "4")
        @NotNull(message = "직급을 선택해 주세요.")
        Long jobPositionId,

        @Schema(description = "권한 (LEADER 또는 MEMBER)", example = "MEMBER")
        @NotNull(message = "권한을 선택해 주세요.")
        Authority role,

        @Schema(description = "역할 id — GET /api/teams 의 해당 부서 roles 에서 고른다. "
                + "보내지 않으면 \"없음\"으로 발급한다", example = "7")
        Long roleId
) {

    public IssueMemberCommand toCommand(Long companyId) {
        return new IssueMemberCommand(companyId, name, email, teamId, jobPositionId, role, roleId);
    }
}
