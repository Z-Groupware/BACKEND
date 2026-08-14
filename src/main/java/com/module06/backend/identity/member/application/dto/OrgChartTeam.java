package com.module06.backend.identity.member.application.dto;

import java.util.List;

/**
 * 조직도(§7-2, §0). 본부 계층 없이 부서(Team)는 평평하게 나열한다(2026-08-07 결정) — team 끼리
 * 부모-자식이 없다. 그 아래는 Team → SubTeam → Member 3단이다 — SubTeam 은 roleLabel 로 묶은
 * 응답 전용 그룹이지 별도 테이블이 아니다({@link OrgChartSubTeam} 참고).
 *
 * @param teamId 팀 미배정 묶음("미배정")만 {@code null} 이다 — 실제 team 행이 없으므로 내려줄 id 가
 *               없다. 그 외에는 항상 실제 team 의 id 다(2026-08-14).
 */
public record OrgChartTeam(
        Long teamId,
        String name,
        List<OrgChartSubTeam> subTeams
) {
}
