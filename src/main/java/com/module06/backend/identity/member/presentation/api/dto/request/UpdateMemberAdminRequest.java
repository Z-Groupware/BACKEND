package com.module06.backend.identity.member.presentation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리 권한(겸직) 부여·회수")
public record UpdateMemberAdminRequest(

        @Schema(description = "관리 권한 부여 여부", example = "true")
        @NotNull(message = "isAdmin 값을 지정해 주세요.")
        Boolean isAdmin
) {
}
