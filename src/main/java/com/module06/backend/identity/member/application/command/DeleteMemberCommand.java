package com.module06.backend.identity.member.application.command;

/**
 * §7 사원 삭제. {@code actingMemberId} 는 본인 삭제 차단(CANNOT_MODIFY_SELF) 검사에만 쓰고
 * 저장하지 않는다 — {@link UpdateMemberRoleCommand} 와 같은 규칙이다.
 */
public record DeleteMemberCommand(Long companyId, Long actingMemberId, Long targetMemberId) {
}
