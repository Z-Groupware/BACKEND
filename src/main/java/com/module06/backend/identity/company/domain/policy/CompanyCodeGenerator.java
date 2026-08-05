package com.module06.backend.identity.company.domain.policy;

import java.util.random.RandomGenerator;

/**
 * 기업 코드를 만든다.
 *
 * <p>이 클래스가 회사 이름·연도·사업자번호를 인자로 받지 않는 것이 핵심이다. 기업 코드는 로그인
 * 1단계의 열쇠이고 토큰 없이 확인할 수 있어서, 추측 가능한 규칙으로 만들면 그것만으로 고객사 명단이
 * 열거된다. {@code TECHSTART-2025} 처럼 회사명에서 끌어오면 경우의 수가 1이다. 받을 수 있는 인자를
 * 두지 않는 것으로 그 실수를 구조적으로 막는다.
 *
 * <p>문자표에서 {@code I·L·O·U} 를 뺐다. 사람이 메일로 받아 손으로 입력하는 값이라 {@code 0}/{@code O},
 * {@code 1}/{@code I}/{@code l} 을 구분하지 못한다. 짝에서 글자 쪽을 빼면 남은 숫자가 모호하지 않다.
 * {@code U} 는 뜻하지 않은 욕설이 만들어지는 것을 막기 위해 뺀다(Crockford Base32 와 같은 선택).
 *
 * <p>남는 문자가 32개이므로 8자면 32<sup>8</sup> ≈ 1.1조 이다. IP 당 분당 20회 제한 아래에서 전수 탐색에
 * 7만 년이 걸리므로, 열거 방어가 레이트 리밋이 아니라 코드 자체에서 성립한다.
 *
 * <p>{@code XXXX-XXXX} 9자로 끊는 것은 기존 코드 {@code NOVA-7K3D} 와 모양을 맞추기 위한 것이다.
 * 화면 placeholder·안내 메일·{@code VARCHAR(20)} 컬럼을 건드리지 않아도 된다.
 *
 * <p>중복은 이 클래스가 다루지 않는다. 32<sup>8</sup> 에서 충돌 확률은 무시할 수 있지만 0 이 아니고,
 * {@code UK_COMPANY_CODE} 유니크 제약이 최종 방어선이다. 발급하는 쪽이 제약 위반을 잡아 다시 생성해야
 * 한다 — 조회 후 INSERT 만 하면 동시 요청에서 둘 다 통과한다(V2.2.8 의 {@code business_number} 와 같다).
 *
 * <p>{@link RandomGenerator} 를 주입받는다. 운영에서는 반드시 {@link java.security.SecureRandom} 을
 * 넣어야 한다 — {@link java.util.Random} 은 값 몇 개로 시드를 복원할 수 있어서, 코드 하나가 새면 그
 * 전후에 발급된 코드가 전부 계산된다.
 */
public class CompanyCodeGenerator {

    /** 32자. I·L·O·U 를 뺐다. 순서·길이를 바꾸면 경우의 수가 바뀐다. */
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int LENGTH = 8;
    private static final int GROUP_SIZE = 4;

    private final RandomGenerator random;

    public CompanyCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    /**
     * 운영에서 쓸 생성기. 무작위 원천을 고를 여지를 남기지 않기 위해 둔다.
     *
     * <p>생성자를 열어 둔 것은 테스트에서 값을 고정하기 위한 것뿐이다. 실제 발급 경로는 이 팩토리를
     * 쓴다.
     */
    public static CompanyCodeGenerator secure() {
        return new CompanyCodeGenerator(new java.security.SecureRandom());
    }

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == GROUP_SIZE) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
