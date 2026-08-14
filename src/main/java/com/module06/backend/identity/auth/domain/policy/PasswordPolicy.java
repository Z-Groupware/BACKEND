package com.module06.backend.identity.auth.domain.policy;

import java.util.regex.Pattern;

/**
 * 사용자가 직접 정하는 비밀번호가 지켜야 할 규칙. 마이페이지 변경(PATCH /api/auth/me/password)과
 * 서버가 만드는 발급 비밀번호({@code PasswordGenerator})가 <b>같은 이 규칙</b>을 본다.
 *
 * <p>둘이 갈리면 "메일로 받은 비밀번호가 우리 정책을 통과하지 못하는" 상태가 된다. 그래서 상수를
 * 여기 한 곳에만 두고, 생성기 테스트가 {@link #isSatisfiedBy} 로 자기 결과를 검사한다.
 *
 * <h2>왜 이 값인가</h2>
 *
 * <p><b>8~16자.</b> 상한이 16자인 것은 사용성 때문만이 아니다 — BCrypt 는 72<b>바이트</b>를 넘는
 * 입력을 조용히 잘라낸다. {@link #ALLOWED_CHARACTERS} 가 출력 가능 ASCII 만 허용하므로 1자 = 1바이트고,
 * 16자는 그 한계에 닿을 수 없다. 문자 집합을 넓히려면 이 계산을 다시 해야 한다.
 *
 * <p><b>영문·숫자·특수문자 3종 필수.</b> 대문자와 소문자를 따로 요구하지 않는다. 8자 최소 길이에서
 * 4종을 모두 강제하면 사용자가 규칙을 맞추느라 오히려 예측 가능한 값(Abcd1234!)으로 몰린다.
 *
 * <p><b>공백 불허.</b> {@code [!-~]}(0x21~0x7E)가 공백·탭·한글을 한 번에 막는다. 메일에서 비밀번호를
 * 복사할 때 앞뒤 공백이 딸려 오는 사고를 입력 단계에서 끊고, 위의 바이트 계산도 이 집합이 지킨다.
 * 특수문자 종류는 좁히지 않는다 — 화이트리스트를 만들면 사용자가 "이건 왜 안 되냐"로 막힌다.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 16;

    /** 허용 문자 집합: 출력 가능 ASCII 에서 공백을 뺀 것. */
    public static final String ALLOWED_CHARACTERS = "[!-~]";

    /**
     * 문자 구성 규칙. 길이는 여기서 보지 않는다 — {@code @Size} 가 따로 본다.
     *
     * <p>하나의 정규식에 길이까지 합치면 "8자 미만"인지 "특수문자 없음"인지 사용자가 알 수 없는
     * 에러 하나로 뭉개진다.
     */
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9])" + ALLOWED_CHARACTERS + "+$";

    public static final String LENGTH_MESSAGE = "비밀번호는 8자 이상 16자 이하로 입력해 주세요.";
    public static final String PATTERN_MESSAGE = "영문·숫자·특수문자를 모두 포함해 주세요. 공백은 사용할 수 없습니다.";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private PasswordPolicy() {
    }

    /**
     * 길이까지 포함한 전체 검사. 요청 검증은 {@code @Size}·{@code @Pattern} 이 하므로, 이 메서드는
     * 생성기가 자기 결과를 확인하는 용도다 — 두 경로가 같은 규칙을 본다는 것을 테스트로 못박는다.
     */
    public static boolean isSatisfiedBy(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }
        return COMPILED.matcher(password).matches();
    }
}
