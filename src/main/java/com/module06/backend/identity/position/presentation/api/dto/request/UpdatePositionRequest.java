package com.module06.backend.identity.position.presentation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.module06.backend.identity.member.domain.model.Authority;

@Schema(description = "직급 수정")
public record UpdatePositionRequest(

        @Schema(description = "직급명 (20자 이하)", example = "수석")
        @NotBlank(message = "직급명을 입력해 주세요.")
        @Size(max = 20, message = "직급명은 20자 이하로 입력해 주세요.")
        String name,

        @Schema(description = "이 직급으로 발급되는 계정의 권한 (LEADER 또는 MEMBER)", example = "LEADER")
        @NotNull(message = "권한을 선택해 주세요.")
        Authority authority,

        @Schema(description = "직급 권한 요약", example = "팀 회의 개설, 팀원 액션 배정, 중간 승인")
        @NotBlank(message = "설명을 입력해 주세요.")
        @Size(max = 200, message = "설명은 200자 이하로 입력해 주세요.")
        String description
) {
}
