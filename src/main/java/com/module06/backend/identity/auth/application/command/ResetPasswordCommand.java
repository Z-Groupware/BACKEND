package com.module06.backend.identity.auth.application.command;

/**
 * 비밀번호 찾기 입력. 로그인과 같은 두 값을 받는다.
 *
 * <p>이메일만으로는 계정을 특정할 수 없다 — {@code member.email} 은 전역 유일이 아니라
 * {@code UNIQUE (company_id, email)} 이다(V1). 같은 이메일이 여러 회사에 있을 수 있으므로
 * 기업 코드가 없으면 어느 계정의 비밀번호를 바꿔야 할지 알 수 없다.
 *
 * <p>비밀번호를 담지 않는다 — 새 비밀번호는 서버가 만든다. 그래서 {@code toString} 을 가릴
 * 필요도 없다({@link ChangePasswordCommand} 와 다른 점).
 */
public record ResetPasswordCommand(
        String companyCode,
        String email
) {
}
