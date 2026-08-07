package com.module06.backend.identity.member.application.command;

import com.module06.backend.identity.member.domain.model.Authority;

/**
 * 구성원 상세 — 역할·직급 변경(§7-4). {@code actingMemberId} 는 본인 변경 차단(CANNOT_MODIFY_SELF)
 * 검사에만 쓰고 저장하지 않는다.
 */
public record UpdateMemberRoleCommand(
        Long companyId,
        Long actingMemberId,
        Long targetMemberId,
        Authority role,
        Long jobPositionId
) {
}
