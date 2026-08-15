package com.module06.backend.identity.auth.application.usecase;

import com.module06.backend.identity.auth.application.command.ChangePasswordCommand;

public interface ChangeMyPasswordUseCase {

    /**
     * 마이페이지 셀프 비밀번호 변경. 성공하면 <b>그 구성원의 갱신표가 전부 폐기된다</b> —
     * 호출자(화면)는 이 API 가 200 을 주면 토큰을 버리고 로그인 화면으로 보내야 한다.
     *
     * <p>이미 발급된 액세스 토큰은 남은 수명(최대 30분)까지 유효하다. 무상태 서명 토큰이라
     * 서버가 되부를 수 없다 — 로그아웃({@code LogoutUseCase})과 같은 한계다.
     */
    void changePassword(ChangePasswordCommand command);
}
