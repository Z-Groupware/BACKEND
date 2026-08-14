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

        /*
         * 화면의 "역할"(구 sub_team)이다. 인가에 쓰지 않는 라벨이라 role(권한)과 완전히 별개다.
         *
         * null 은 "안 바꾼다"는 뜻이다 — role·jobPositionId 와 달리 @NotNull 을 걸지 않는 이유가
         * 이것이다. 역할 칸을 건드리지 않은 요청까지 400 이 되면, 직급만 고치려는 화면이 역할을
         * 매번 다시 실어야 한다. 비우려면 "없음"(V2.3.9)의 id 를 보낸다 — 역할 미부여를 뜻하는
         * 시스템 행이 실제로 있고, GET /api/teams 의 roles 에도 그 항목이 들어 있다.
         *
         * 이름이 아니라 id 로 받는다(2026-08-14 전환). role 에는 (company_id, name) UNIQUE 가 없어
         * 같은 이름이 두 부서에 하나씩 있을 수 있고, 이름으로 받으면 화면이 고른 역할과 저장되는
         * 행이 갈렸다.
         */
        @Schema(description = "역할 id — GET /api/teams 의 해당 부서 roles 에서 고른다. "
                + "보내지 않으면 그대로 둔다", example = "7")
        Long roleId,

        @Schema(description = "허용되지 않는 필드 — 채워 보내면 400이 난다", hidden = true)
        Boolean isAdmin
) {
}
