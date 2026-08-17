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

    /**
     * 비밀번호 찾기로 재발급한 비밀번호를 보낸다.
     *
     * <p><b>이 메서드만 성공 여부를 돌려준다.</b> {@link #sendAccountIssued} 와 정반대의 이유다 —
     * 계정 발급은 메일이 실패해도 회사·계정이 이미 만들어져 있어 되돌릴 이유가 없지만, 재발급은
     * 발송에 실패한 채로 저장까지 하면 <b>사용자가 새 비밀번호를 모르는 상태로 계정이 잠긴다</b>.
     * 관리자 재발급 경로도 없어서 복구가 불가능하다.
     *
     * <p>그래서 호출자는 이 값이 {@code true} 일 때만 새 비밀번호를 저장한다. 실패하면 기존
     * 비밀번호가 그대로 살아 있어 사용자는 아무것도 잃지 않는다.
     *
     * <p>{@link #sendAccountIssued} 와 똑같이 기업 코드를 함께 싣는다. 요청할 때 이미 입력한 값이라
     * 새 정보는 아니지만, 로그인은 기업 코드가 있어야 1단계를 넘는다 — 메일 하나만 열면 로그인에
     * 필요한 것이 다 있어야 한다.
     *
     * @param password 평문이다. 메일 본문에 실어야 하므로 이 지점에서만 평문을 다룬다.
     * @return 발송에 성공했으면 {@code true}
     */
    boolean sendPasswordReset(String toEmail, String companyCode, String password);
}
