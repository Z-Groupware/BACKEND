package com.module06.backend.identity.team.presentation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "부서 이름 수정")
public record RenameTeamRequest(

        @Schema(description = "부서명 (20자 이하)", example = "제품개발팀")
        @NotBlank(message = "부서명을 입력해 주세요.")
        @Size(max = 20, message = "부서명은 20자 이하로 입력해 주세요.")
        String name
) {
}
