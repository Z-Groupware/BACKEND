package com.module06.backend.identity.team.presentation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "부서 안 역할 이름 수정 (§6-11)")
public record RenameTeamRoleRequest(

        @Schema(description = "역할명 (50자 이하)", example = "서버")
        @NotBlank(message = "역할명을 입력해 주세요.")
        @Size(max = 50, message = "역할명은 50자 이하로 입력해 주세요.")
        String name
) {
}
