package com.module06.backend.identity.team.presentation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "부서 안 역할 생성 (§6-10)")
public record CreateTeamRoleRequest(

        /* role.name 은 VARCHAR(50) 이다(V1) — 그보다 길면 데이터베이스에서 잘리거나 터진다. */
        @Schema(description = "역할명 (50자 이하)", example = "백엔드")
        @NotBlank(message = "역할명을 입력해 주세요.")
        @Size(max = 50, message = "역할명은 50자 이하로 입력해 주세요.")
        String name
) {
}
