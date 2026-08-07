package com.module06.backend.identity.company.presentation.api.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.module06.backend.identity.company.application.command.OnboardCompanyCommand;
import com.module06.backend.identity.member.domain.model.Authority;

/** §4-1. 부서(역할 중첩) · 직급 · 초대 명단을 한 번에 커밋한다 — 저장 후 되돌리는 경로는 없다. */
@Schema(description = "온보딩 커밋 — 부서·역할, 직급, 초대 명단을 한 번에 저장하고 계정을 즉시 발급한다")
public record OnboardingRequest(

        @Valid @NotEmpty(message = "부서를 1개 이상 입력해 주세요.")
        List<TeamNode> teams,

        @Valid @NotEmpty(message = "직급을 1개 이상 입력해 주세요.")
        List<JobPositionNode> jobPositions,

        @Valid @NotNull
        List<InviteNode> invites
) {
    @Schema(description = "부서 — 그 아래 역할을 중첩으로 갖는다(깊이 2 고정)")
    public record TeamNode(
            @NotBlank(message = "부서 임시 식별자가 필요합니다.") String tempId,
            @NotBlank(message = "부서명을 입력해 주세요.")
            @Size(max = 20, message = "부서명은 20자 이하로 입력해 주세요.") String name,
            @Valid @NotNull List<SubTeamNode> subTeams
    ) {
    }

    @Schema(description = "역할 — 부서 안의 하위 구분(구 sub_team). 빈 배열 허용")
    public record SubTeamNode(
            @NotBlank(message = "역할 임시 식별자가 필요합니다.") String tempId,
            @NotBlank(message = "역할명을 입력해 주세요.")
            @Size(max = 20, message = "역할명은 20자 이하로 입력해 주세요.") String name
    ) {
    }

    @Schema(description = "직급 — defaultRole 이 그 직급을 받은 구성원의 권한이 된다")
    public record JobPositionNode(
            @NotBlank(message = "직급 임시 식별자가 필요합니다.") String tempId,
            @NotBlank(message = "직급명을 입력해 주세요.")
            @Size(max = 20, message = "직급명은 20자 이하로 입력해 주세요.") String name,
            @NotNull(message = "권한을 선택해 주세요.") Authority defaultRole
    ) {
    }

    @Schema(description = "초대 — 저장 즉시 계정이 발급된다. subTeamTempId 는 없어도 된다")
    public record InviteNode(
            @NotBlank(message = "이름을 입력해 주세요.") String name,
            @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
            @NotBlank(message = "소속 부서를 선택해 주세요.") String teamTempId,
            String subTeamTempId,
            @NotBlank(message = "직급을 선택해 주세요.") String jobPositionTempId,
            boolean isAdmin
    ) {
    }

    public OnboardCompanyCommand toCommand(Long companyId) {
        List<OnboardCompanyCommand.TeamNode> teamNodes = teams.stream()
                .map(t -> new OnboardCompanyCommand.TeamNode(t.tempId(), t.name(),
                        t.subTeams().stream()
                                .map(s -> new OnboardCompanyCommand.SubTeamNode(s.tempId(), s.name()))
                                .toList()))
                .toList();
        List<OnboardCompanyCommand.JobPositionNode> positionNodes = jobPositions.stream()
                .map(p -> new OnboardCompanyCommand.JobPositionNode(p.tempId(), p.name(), p.defaultRole()))
                .toList();
        List<OnboardCompanyCommand.InviteNode> inviteNodes = invites.stream()
                .map(i -> new OnboardCompanyCommand.InviteNode(i.name(), i.email(), i.teamTempId(),
                        i.subTeamTempId(), i.jobPositionTempId(), i.isAdmin()))
                .toList();
        return new OnboardCompanyCommand(companyId, teamNodes, positionNodes, inviteNodes);
    }
}
