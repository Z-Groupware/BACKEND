package com.module06.backend.identity.auth.application.usecase;

/**
 * API 04 — 로그아웃.
 */
public interface LogoutUseCase {

    /**
     * 그 구성원의 갱신표를 전부 지운다.
     *
     * <p>기기별로 끊지 않는 이유: 로그아웃 요청에 바디가 없고 액세스 토큰에는 jti 가 없어서
     * 어느 기기의 갱신표인지 알 수 없다. 회사 업무 시스템에서는 전체 로그아웃이 더 안전하다.
     *
     * <p>액세스 토큰은 상태를 갖지 않아 여기서 취소되지 않는다 — 남은 수명(30분)까지는 통한다.
     * 그 30분을 감수하는 것이 설계다.
     */
    void logout(Long memberId);
}
