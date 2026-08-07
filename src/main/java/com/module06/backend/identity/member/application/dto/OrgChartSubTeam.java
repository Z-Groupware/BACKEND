package com.module06.backend.identity.member.application.dto;

import java.util.List;

/**
 * 조직도의 두 번째 계층(§0: Team → SubTeam → Member, 2026-08-07 확정). 스키마상 SubTeam 테이블은
 * 없다 — member.roleLabel(구 sub_team, V2.3.4) 문자열로 묶은 응답 전용 그룹이다.
 * {@code roleLabel} 은 role_id 가 NOT NULL 이라 실사용에서 null 이 아니다("없음"도 실제 role 행).
 */
public record OrgChartSubTeam(
        String roleLabel,
        List<OrgChartMember> members
) {
}
