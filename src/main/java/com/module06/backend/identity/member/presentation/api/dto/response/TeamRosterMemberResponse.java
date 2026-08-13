package com.module06.backend.identity.member.presentation.api.dto.response;

import com.module06.backend.identity.member.application.dto.TeamRosterMember;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회의 참석자 선택용 팀 로스터 한 줄")
public record TeamRosterMemberResponse(
        @Schema(description = "구성원 id", example = "3")
        Long memberId,

        @Schema(description = "이름", example = "이하윤")
        String name
) {

    public static TeamRosterMemberResponse from(TeamRosterMember member) {
        return new TeamRosterMemberResponse(member.memberId(), member.name());
    }
}
