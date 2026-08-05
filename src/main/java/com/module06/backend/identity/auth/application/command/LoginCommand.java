package com.module06.backend.identity.auth.application.command;

/** {@code keepSignedIn} 은 리프레시 수명만 바꾼다(1일 ↔ 14일). 액세스 수명은 30분 고정이다. */
public record LoginCommand(
        String companyCode,
        String email,
        String password,
        boolean keepSignedIn
) {
}
