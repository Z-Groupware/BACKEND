package com.module06.backend.identity.auth.presentation.api.dto.request;

import com.module06.backend.identity.auth.application.command.ResetPasswordCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호 찾기 요청. 로그인 화면과 같은 두 값을 받는다 — 이메일만으로는 계정을 특정할 수 없다
 * ({@link ResetPasswordCommand} javadoc).
 *
 * <p>비밀번호를 받지 않으므로 {@code toString} 을 가리지 않는다. 여기 담기는 것은 로그에 남아도
 * 되는 값이다({@code ChangeMyPasswordRequest} 와 다른 점).
 */
@Schema(description = "비밀번호 찾기 — 새 비밀번호를 만들어 이메일로 보낸다")
public record ResetPasswordRequest(

        @Schema(description = "기업 코드", example = "8AS2-G8T1")
        @NotBlank(message = "기업 코드를 입력해 주세요.")
        String companyCode,

        @Schema(description = "로그인 이메일")
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {

    public ResetPasswordCommand toCommand() {
        return new ResetPasswordCommand(companyCode, email);
    }
}
