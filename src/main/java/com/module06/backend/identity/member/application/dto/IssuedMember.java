package com.module06.backend.identity.member.application.dto;

import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;

/** 계정 발급 완료 응답(§5-1). 비밀번호는 담지 않는다 — 메일로만 나간다. */
public record IssuedMember(
        Long memberId,
        String name,
        String email,
        String teamName,
        String positionName,
        Authority role,
        boolean isAdmin,
        MemberStatus workStatus
) {
}
