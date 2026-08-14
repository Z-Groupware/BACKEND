package com.module06.backend.identity.auth.application.command;

/**
 * 마이페이지 비밀번호 변경 입력. {@code memberId} 는 요청 바디가 아니라 액세스 토큰에서 온다 —
 * 바디로 받으면 남의 비밀번호를 바꿀 수 있다.
 *
 * <p>{@code newPasswordConfirm} 을 서비스까지 들고 오는 이유: 확인값 불일치를 {@code @Valid} 로
 * 처리하면 다른 형식 오류와 뭉쳐 {@code INVALID_INPUT_VALUE} 하나가 되어, 화면이 "확인칸이 다르다"만
 * 따로 표시할 수 없다.
 */
public record ChangePasswordCommand(
        Long memberId,
        String currentPassword,
        String newPassword,
        String newPasswordConfirm
) {

    /**
     * 레코드가 자동 생성하는 {@code toString} 은 모든 필드를 그대로 찍는다. 예외 로그에 이 객체가
     * 한 번만 실려도 평문 비밀번호가 로그에 남으므로 반드시 덮어쓴다.
     */
    @Override
    public String toString() {
        return "ChangePasswordCommand[memberId=" + memberId + ", passwords=****]";
    }
}
