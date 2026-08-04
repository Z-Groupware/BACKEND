package com.module06.backend.project.domain.repository;

import java.util.List;

/* comment.
    team(B 도메인) 소속 검증 계약. 실제 조회는 infrastructure가 구현한다.
*/
public interface TeamReferenceRepository {

    List<Long> findExistingTeamIds(List<Long> teamIds, Long companyId);
}
