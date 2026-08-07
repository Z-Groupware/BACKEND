package com.module06.backend.identity.member.presentation.api.dto.request;

import com.module06.backend.identity.member.domain.model.Authority;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 구성원 상세 — 역할·직급 변경(§7-4). {@code isAdmin} 은 이 요청에 없다 — 왔다면 컨트롤러가
 * {@code 400 FIELD_NOT_ALLOWED} 로 막는다(어드민의 자기 복제 차단, §7-7이 오너 전용인 이유).
 */
@Schema(description = "구성원 역할·직급 변경")
public record UpdateMemberRoleRequest(

        @Schema(description = "권한 (LEADER 또는 MEMBER)", example = "LEADER")
        @NotNull(message = "권한을 선택해 주세요.")
        Authority role,

        @Schema(description = "직급 id", example = "6")
        @NotNull(message = "직급을 선택해 주세요.")
        Long jobPositionId,

        @Schema(description = "허용되지 않는 필드 — 채워 보내면 400이 난다", hidden = true)
        Boolean isAdmin
) {
}
