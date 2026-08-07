package com.module06.backend.identity.member.application.command;

import com.module06.backend.identity.member.domain.model.Authority;

/**
 * 계정 발급(§5-1). {@code roleLabel} 은 화면 폼에 없어 항상 {@code null} 로 들어온다 — 값이 오면
 * 회사 안에서 이름으로 찾고, 없으면 {@code MEMBER_ROLE_NOT_ASSIGNABLE} 이 아니라 못 찾음으로 취급해
 * 404 로 답한다(§7-3의 "roleLabel 편집 UI 미확인" 메모와 같은 이유로 아직 검증 규칙이 없다).
 */
public record IssueMemberCommand(
        Long companyId,
        String name,
        String email,
        Long teamId,
        Long jobPositionId,
        Authority role,
        String roleLabel
) {
}
