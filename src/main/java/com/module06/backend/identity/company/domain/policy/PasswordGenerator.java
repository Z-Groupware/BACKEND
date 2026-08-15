package com.module06.backend.identity.company.domain.policy;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

import com.module06.backend.identity.auth.domain.policy.PasswordPolicy;

/**
 * 계정 발급 시 서버가 만드는 비밀번호. 12자다.
 *
 * <p>이 값은 계정의 실제 비밀번호다. 사용자는 마이페이지에서 스스로 바꿀 수 있지만
 * (PATCH /api/auth/me/password), <b>바꾸도록 강제하지는 않는다</b> — 안 바꾸면 이 값을 계속 쓴다.
 * 최초 로그인 시 안내를 한 번 띄울 뿐이다. 잃어버렸을 때의 복구 경로는 아직 없다.
 *
 * <p>사용자가 고르지 않고 서버가 만드는 것이 핵심이다. 사람이 고르게 두면 회사 이름·1234 같은 값이
 * 계정 발급 시점에 그대로 굳는다.
 *
 * <p>발급자에게도 보여주지 않는다. 메일로만 나가므로 계정을 만든 어드민조차 남의 계정으로 로그인할
 * 수 없다 — 사칭이 정책이 아니라 구조로 막힌다.
 *
 * <p>{@link CompanyCodeGenerator} 와 달리 혼동 문자를 빼지 않는다. 기업 코드는 사람이 눈으로 보고
 * 손으로 옮겨 적지만 비밀번호는 복사해 붙여넣는 값이라, 문자 종류를 줄이면 경우의 수만 깎인다.
 * 대소문자·숫자·기호를 섞어 70자 알파벳에서 뽑으므로 70<sup>12</sup> ≈ 1.4×10<sup>22</sup> 이다.
 *
 * <p><b>영문·숫자·특수문자가 각각 최소 한 번 나오도록 강제한다.</b> 예전에는 강제하지 않았는데,
 * 그러면 12자를 균등하게 뽑는 특성상 숫자가 통째로 빠지는 경우가 약 15%, 특수문자가 빠지는 경우가
 * 약 23% 생긴다 — 사용자가 마이페이지에서 지켜야 하는 {@link PasswordPolicy} 를 정작 서버가 발급한
 * 비밀번호가 위반하는 상태가 된다. 세 자리를 고정하는 만큼 경우의 수가 줄지만 자릿수는 그대로다.
 *
 * <p>운영에서는 반드시 {@link SecureRandom} 을 넣는다 — {@link java.util.Random} 은 값 몇 개로 시드가
 * 복원되어, 비밀번호 하나가 새면 그 전후로 발급된 계정이 전부 열린다.
 */
public class PasswordGenerator {

    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIALS = "!@#$%^&*";
    private static final String ALPHABET = LETTERS + DIGITS + SPECIALS;

    /** {@link PasswordPolicy#MAX_LENGTH} 이하여야 한다 — 정책을 넘는 값을 발급할 수는 없다. */
    private static final int LENGTH = 12;

    private final RandomGenerator random;

    public PasswordGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** 운영 경로. 생성자를 열어 둔 것은 테스트에서 값을 고정하기 위한 것뿐이다. */
    public static PasswordGenerator secure() {
        return new PasswordGenerator(new SecureRandom());
    }

    public String generate() {
        char[] password = new char[LENGTH];

        // 정책이 요구하는 세 종류를 먼저 한 자리씩 채운다.
        password[0] = pick(LETTERS);
        password[1] = pick(DIGITS);
        password[2] = pick(SPECIALS);
        for (int i = 3; i < LENGTH; i++) {
            password[i] = pick(ALPHABET);
        }

        // 섞지 않으면 "1·2·3번째 자리가 무슨 종류인지"가 항상 같아 앞 세 자리의 추측 범위가 좁아진다.
        shuffle(password);
        return new String(password);
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }

    /** Fisher-Yates. 뒤에서부터 앞의 임의 위치와 바꾼다 — 모든 순열이 같은 확률로 나온다. */
    private void shuffle(char[] password) {
        for (int i = password.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = password[i];
            password[i] = password[j];
            password[j] = swap;
        }
    }
}
