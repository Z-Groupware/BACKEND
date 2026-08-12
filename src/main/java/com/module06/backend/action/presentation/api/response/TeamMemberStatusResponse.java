package com.module06.backend.action.presentation.api.response;

import java.util.List;

import com.module06.backend.action.application.usecase.GetTeamMemberStatusUseCase.TeamMemberItem;
import com.module06.backend.action.application.usecase.GetTeamMemberStatusUseCase.TeamMemberStatusList;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ReferenceMemberStatus;

/* comment.
    팀 대시보드 "팀원 현황" 행 응답. 이슈 #352.
*/
public record TeamMemberStatusResponse(
        Long memberId, String name, String positionName, String roleName,
        ReferenceMemberStatus status, long actionCount) {

    public static TeamMemberStatusResponse from(TeamMemberItem item) {
        return new TeamMemberStatusResponse(
                item.memberId(), item.name(), item.positionName(), item.roleName(), item.status(), item.actionCount());
    }

    public static List<TeamMemberStatusResponse> from(TeamMemberStatusList list) {
        return list.items().stream().map(TeamMemberStatusResponse::from).toList();
    }
}
