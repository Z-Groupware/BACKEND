package com.module06.backend.identity.auth.application.command;

/** {@code keepSignedIn} 은 리프레시 수명만 바꾼다(1일 ↔ 14일). 액세스 수명은 30분 고정이다. */
public record LoginCommand(
        String companyCode,
        String email,
        String password,
        boolean keepSignedIn
) {

    /**
     * 레코드가 자동 생성하는 {@code toString} 은 {@code password} 를 평문 그대로 찍는다. 예외 로그에
     * 이 객체가 한 번만 실려도 비밀번호가 로그에 남으므로 덮어쓴다
     * ({@code ChangePasswordCommand} 와 같은 이유).
     */
    @Override
    public String toString() {
        return "LoginCommand[companyCode=" + companyCode + ", email=" + email
                + ", password=****, keepSignedIn=" + keepSignedIn + "]";
    }
}
