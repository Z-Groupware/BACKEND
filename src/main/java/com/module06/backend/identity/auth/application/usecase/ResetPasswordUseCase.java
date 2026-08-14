package com.module06.backend.identity.auth.application.usecase;

import com.module06.backend.identity.auth.application.command.ResetPasswordCommand;

public interface ResetPasswordUseCase {

    /**
     * 비밀번호 찾기. 서버가 새 비밀번호를 만들어 메일로 보내고, 그 계정의 갱신표를 전부 폐기한다.
     *
     * <p>응답에는 새 비밀번호를 담지 않는다 — 담으면 이메일 소유를 확인하지 않고도 남의 계정
     * 비밀번호를 화면에서 읽을 수 있게 되어, 이 기능이 곧 계정 탈취 도구가 된다. 메일이
     * 비밀번호가 사용자에게 도달하는 유일한 경로다.
     */
    void resetPassword(ResetPasswordCommand command);
}
