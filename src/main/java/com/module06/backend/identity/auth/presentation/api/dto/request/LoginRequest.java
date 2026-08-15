package com.module06.backend.identity.auth.presentation.api.dto.request;

import com.module06.backend.identity.auth.application.command.LoginCommand;

import jakarta.validation.constraints.NotBlank;

/**
 * 필드 이름은 명세 §2-2 그대로다 — 프론트가 이 키로 보낸다.
 *
 * <p>비밀번호에 형식 검증을 걸지 않는다. 로그인은 이미 발급된 비밀번호를 확인하는 자리이고, 형식으로
 * 미리 걸러내면 "이 비밀번호는 규칙에 맞지 않는다"는 응답이 규칙을 알려준다. 틀린 값은 모두
 * {@code LOGIN_FAILED} 로 같게 답한다.
 *
 * <p>{@code keepSignedIn} 이 원시 {@code boolean} 이 아니라 {@code Boolean} 인 이유: 이 필드를 빼고
 * 보내면 원시형에서는 역직렬화가 실패해 400 이 된다. 체크박스를 끈 채로 키를 보내지 않는 프론트가
 * 로그인을 아예 못 하게 되므로, 없으면 {@code false} 로 본다.
 */
public record LoginRequest(
        @NotBlank(message = "기업 코드를 입력해 주세요.")
        String companyCode,

        @NotBlank(message = "이메일을 입력해 주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password,

        Boolean keepSignedIn
) {

    public LoginCommand toCommand() {
        return new LoginCommand(companyCode, email, password, Boolean.TRUE.equals(keepSignedIn));
    }

    /**
     * 레코드가 자동 생성하는 {@code toString} 은 {@code password} 를 평문 그대로 찍는다. 역직렬화
     * 실패나 검증 예외 로그에 이 객체가 실리면 비밀번호가 그대로 남으므로 덮어쓴다
     * ({@code ChangeMyPasswordRequest} 와 같은 이유).
     */
    @Override
    public String toString() {
        return "LoginRequest[companyCode=" + companyCode + ", email=" + email
                + ", password=****, keepSignedIn=" + keepSignedIn + "]";
    }
}
