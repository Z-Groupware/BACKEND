package com.module06.backend.identity.company.domain.policy;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

public class CompanyCodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int LENGTH = 8;
    private static final int GROUP_SIZE = 4;

    private final RandomGenerator random;

    public CompanyCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** 운영 발급 경로. 생성자는 테스트에서 값을 고정하기 위한 것이고, java.util.Random 을 넣으면 전후 발급분이 계산된다. */
    public static CompanyCodeGenerator secure() {
        return new CompanyCodeGenerator(new SecureRandom());
    }

    /** 중복을 검사하지 않는다. UK_COMPANY_CODE 위반을 호출자가 잡아 재생성해야 한다. */
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
