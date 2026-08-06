package com.module06.backend.identity.company.domain.policy;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * 계정 발급 시 서버가 만드는 임시 비밀번호. 12자다.
 *
 * <p>사용자가 고르지 않고 서버가 만드는 것이 핵심이다. 비밀번호 변경 기능이 없어서 이 값이 계정의
 * 유일한 비밀번호가 되므로, 사람이 고르게 두면 회사 이름·1234 같은 값이 그대로 굳는다.
 *
 * <p>발급자에게도 보여주지 않는다. 메일로만 나가므로 계정을 만든 어드민조차 남의 계정으로 로그인할
 * 수 없다 — 사칭이 정책이 아니라 구조로 막힌다.
 *
 * <p>{@link CompanyCodeGenerator} 와 달리 혼동 문자를 빼지 않는다. 기업 코드는 사람이 눈으로 보고
 * 손으로 옮겨 적지만 비밀번호는 복사해 붙여넣는 값이라, 문자 종류를 줄이면 경우의 수만 깎인다.
 * 대소문자·숫자·기호 4종을 섞어 72자 알파벳에서 뽑으므로 72<sup>12</sup> ≈ 1.9×10<sup>22</sup> 이다.
 *
 * <p>4종이 각각 최소 한 번 나오도록 강제하지 않는다. 강제하면 그만큼 경우의 수가 줄고, 12자에서
 * 특정 종류가 통째로 빠질 확률은 무시할 수 있다. 우리 검증 규칙도 조합을 요구하지 않는다.
 *
 * <p>운영에서는 반드시 {@link SecureRandom} 을 넣는다 — {@link java.util.Random} 은 값 몇 개로 시드가
 * 복원되어, 비밀번호 하나가 새면 그 전후로 발급된 계정이 전부 열린다.
 */
public class TemporaryPasswordGenerator {

    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*";
    private static final int LENGTH = 12;

    private final RandomGenerator random;

    public TemporaryPasswordGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** 운영 경로. 생성자를 열어 둔 것은 테스트에서 값을 고정하기 위한 것뿐이다. */
    public static TemporaryPasswordGenerator secure() {
        return new TemporaryPasswordGenerator(new SecureRandom());
    }

    public String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
