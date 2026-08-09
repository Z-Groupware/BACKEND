package com.module06.backend.identity.company.application.command;

import java.util.List;

import com.module06.backend.identity.member.domain.model.Authority;

/**
 * §4-1 온보딩 커밋. {@code tempId} 는 화면이 로컬 state에서 붙인 임시 식별자다 — 서버는 저장
 * 순서대로 실제 id를 채번하고, 같은 요청 안에서 tempId→실제 id 매핑만 메모리에 들고 있으면 되므로
 * 별도 스킴이 필요 없다.
 */
public record OnboardCompanyCommand(
        Long companyId,
        List<TeamNode> teams,
        List<JobPositionNode> jobPositions,
        List<InviteNode> invites
) {
    public record TeamNode(String tempId, String name, List<SubTeamNode> subTeams) {
    }

    /** "역할"(부서 안의 하위 구분, 구 sub_team). */
    public record SubTeamNode(String tempId, String name) {
    }

    public record JobPositionNode(String tempId, String name, Authority defaultRole) {
    }

    public record InviteNode(String name, String email, String teamTempId, String subTeamTempId,
                              String jobPositionTempId, boolean isAdmin) {
    }
}
