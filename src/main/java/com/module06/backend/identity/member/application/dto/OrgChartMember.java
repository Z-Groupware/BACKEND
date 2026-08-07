package com.module06.backend.identity.member.application.dto;

import com.module06.backend.identity.member.domain.model.Authority;

/** 조직도 카드 한 장(§7-2). 이름 + 직급뿐이라 필드를 최소로 유지한다 — 페이징 없이 전 구성원이 담긴다. */
public record OrgChartMember(
        Long memberId,
        String name,
        String positionName,
        Authority role
) {
}
