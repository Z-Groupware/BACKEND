package com.module06.backend.identity.team.application.dto;

import java.util.List;

/**
 * 부서 한 줄. {@code roles} 는 이 부서에서 고를 수 있는 역할 전부다 — 이 부서 소유 역할에
 * 시스템 역할 "없음"(V2.3.9)이 항상 앞에 붙는다. 아무도 배정되지 않은 역할도 들어간다(조직도
 * {@code /api/members/org-chart} 와 다른 점이다 — 그쪽은 사원을 역할로 묶은 결과라 빈 역할이
 * 나올 수 없다).
 */
public record TeamNode(
        Long teamId,
        String name,
        Long leaderMemberId,
        String leaderName,
        long memberCount,
        List<RoleNode> roles
) {
}
