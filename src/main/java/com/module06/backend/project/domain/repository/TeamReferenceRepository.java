package com.module06.backend.project.domain.repository;

import java.util.List;

/* comment.
    team(B 도메인) 소속 검증·이름 조회 계약. 실제 조회는 infrastructure가 구현한다.
*/
public interface TeamReferenceRepository {

    List<Long> findExistingTeamIds(List<Long> teamIds, Long companyId);

    // 프로젝트 목록의 부서 칩(teamNames) 표시용 — 회사 소속 팀만, id·name 배치 조회 (2026-08-09, 윤종호 확인).
    List<TeamName> findTeamNames(List<Long> teamIds, Long companyId);

    record TeamName(Long id, String name) {
    }
}
