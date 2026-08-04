package com.module06.backend.project.application.policy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.domain.repository.TeamReferenceRepository;
import com.module06.backend.project.exception.ProjectErrorCode;

import lombok.RequiredArgsConstructor;

/* comment.
    지정하려는 teamIds가 전부 요청자의 회사 소속인지 검증한다(타 회사 팀 연결 IDOR 방지).
*/
@Component
@RequiredArgsConstructor
public class ProjectTeamOwnershipPolicy {

    private final TeamReferenceRepository teamReferenceRepository;

    public void check(List<Long> teamIds, Long companyId) {
        if (teamIds.isEmpty()) {
            return;
        }
        List<Long> existingIds = teamReferenceRepository.findExistingTeamIds(teamIds, companyId);
        if (existingIds.size() != teamIds.size()) {
            throw new BusinessException(ProjectErrorCode.INVALID_TEAM_ASSIGNMENT);
        }
    }
}
