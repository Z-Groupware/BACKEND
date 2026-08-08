package com.module06.backend.identity.auth.presentation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import com.module06.backend.identity.member.application.command.UpdateMyProfileCommand;

/** 마이페이지 "편집" — 부서·직급·전화번호만 셀프로 바꾼다. 전부 선택값, null 필드는 안 바뀐다. */
@Schema(description = "마이페이지 프로필 수정 — null 필드는 값을 바꾸지 않는다")
public record UpdateMyProfileRequest(

        @Schema(description = "소속 부서 id")
        Long teamId,

        @Schema(description = "직급 id")
        Long positionId,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @Size(max = 30, message = "전화번호는 30자 이하로 입력해 주세요.")
        String phone
) {
    public UpdateMyProfileCommand toCommand(Long memberId, Long companyId) {
        return new UpdateMyProfileCommand(memberId, companyId, teamId, positionId, phone);
    }
}
