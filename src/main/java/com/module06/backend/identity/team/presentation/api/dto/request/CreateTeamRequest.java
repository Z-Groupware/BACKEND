package com.module06.backend.identity.team.presentation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "부서 생성")
public record CreateTeamRequest(

        @Schema(description = "부서명 (5자 이하)", example = "사업본부")
        @NotBlank(message = "부서명을 입력해 주세요.")
        @Size(max = 5, message = "부서명은 5자 이하로 입력해 주세요.")
        String name,

        @Schema(description = "상위 부서 id. 없으면 최상위(본부)", example = "null")
        Long parentTeamId
) {
}
