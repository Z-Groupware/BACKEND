package com.module06.backend.identity.member.application.command;

import com.module06.backend.identity.member.domain.model.Authority;

/**
 * 계정 발급(§5-1). {@code roleId} 는 선택이다 — null 이면 "없음"(V2.3.9)으로 발급한다. 값이 오면
 * 이 회사·{@code teamId} 부서의 역할이어야 하고, 아니면 {@code MEMBER_ROLE_LABEL_NOT_FOUND} 로
 * 404 를 답한다. 화면은 {@code GET /api/teams} 가 부서마다 실어 주는 역할 목록에서 고른다.
 */
public record IssueMemberCommand(
        Long companyId,
        String name,
        String email,
        Long teamId,
        Long jobPositionId,
        Authority role,
        Long roleId
) {
}
