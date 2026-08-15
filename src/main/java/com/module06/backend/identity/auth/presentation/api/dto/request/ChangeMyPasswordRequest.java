package com.module06.backend.identity.auth.presentation.api.dto.request;

import com.module06.backend.identity.auth.application.command.ChangePasswordCommand;
import com.module06.backend.identity.auth.domain.policy.PasswordPolicy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 마이페이지 비밀번호 변경 요청.
 *
 * <p>{@code currentPassword} 에는 형식 검증을 걸지 않는다 — {@code LoginRequest} 와 같은 이유다.
 * 이미 발급된 값을 확인하는 자리라, 형식으로 미리 걸러내면 규칙보다 먼저 만들어진 비밀번호를 쓰는
 * 사람이 변경 자체를 못 하게 된다.
 *
 * <p>{@code newPasswordConfirm} 에도 규칙을 걸지 않는다. 확인칸은 "새 비밀번호와 같은가"만 보면
 * 되고, 양쪽에 같은 규칙을 걸면 오타 하나에 에러가 두 줄로 뜬다.
 */
@Schema(description = "마이페이지 비밀번호 변경 — 현재 비밀번호로 본인 확인 후 교체한다")
public record ChangeMyPasswordRequest(

        @Schema(description = "지금 쓰고 있는 비밀번호")
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        String currentPassword,

        @Schema(description = "새 비밀번호 — 8~16자, 영문·숫자·특수문자 포함, 공백 불가")
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = PasswordPolicy.LENGTH_MESSAGE)
        @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.PATTERN_MESSAGE)
        String newPassword,

        @Schema(description = "새 비밀번호 확인 — newPassword 와 같아야 한다")
        @NotBlank(message = "새 비밀번호를 한 번 더 입력해 주세요.")
        String newPasswordConfirm
) {

    public ChangePasswordCommand toCommand(Long memberId, Long companyId) {
        return new ChangePasswordCommand(memberId, companyId, currentPassword, newPassword, newPasswordConfirm);
    }

    /**
     * 레코드가 자동 생성하는 {@code toString} 은 모든 필드를 그대로 찍는다. 역직렬화 실패나 검증
     * 예외 로그에 이 객체가 한 번만 실려도 평문 비밀번호 세 개가 로그에 남으므로 반드시 덮어쓴다.
     *
     * <p>검증 실패 <b>응답</b>은 이미 안전하다 — {@code GlobalExceptionHandler} 가 필드명과 메시지만
     * 담고 입력값({@code rejectedValue})은 담지 않는다. 위험한 쪽은 응답이 아니라 로그다.
     */
    @Override
    public String toString() {
        return "ChangeMyPasswordRequest[passwords=****]";
    }
}
