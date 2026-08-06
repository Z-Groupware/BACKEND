package com.module06.backend.identity.company.application.port.out;

/**
 * 계정 정보를 본인에게 보낸다. 비밀번호가 사용자에게 도달하는 유일한 경로다.
 *
 * <p>반드시 트랜잭션 <b>밖에서</b> 불린다. 메일 서버가 느려 타임아웃이 나면 방금 만든 회사가 통째로
 * 사라지는데, 회사는 정상적으로 만들어졌고 메일만 못 보낸 것이므로 롤백할 이유가 없다.
 * 코드는 이미 DB 에 있으니 재발송으로 해결한다.
 *
 * <p>같은 이유로 예외를 던지지 않는 구현이 맞다. 호출자가 잡아 봐야 할 수 있는 일이 없다.
 */
public interface AccountMailPort {

    /**
     * 기업 코드·이메일·비밀번호 3개를 보낸다.
     *
     * <p>기업 코드가 빠지면 로그인 1단계를 넘지 못한다 — 비밀번호만 보내면 받는 사람이 로그인할
     * 방법이 없다.
     *
     * @param password 평문이다. 메일 본문에 실어야 하므로 이 지점에서만 평문을 다룬다.
     */
    void sendAccountIssued(String toEmail, String companyCode, String password);
}
