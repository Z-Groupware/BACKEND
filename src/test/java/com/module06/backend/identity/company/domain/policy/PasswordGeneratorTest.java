package com.module06.backend.identity.company.domain.policy;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.identity.auth.domain.policy.PasswordPolicy;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 이 테스트의 요점은 하나다 — 서버가 발급한 비밀번호가 사용자에게 요구하는 규칙을 스스로 지킨다.
 *
 * 예전 구현은 문자 종류를 보장하지 않아서, 12자를 균등하게 뽑는 특성상 숫자가 통째로 빠지는 경우가
 * 약 15%, 특수문자가 빠지는 경우가 약 23% 있었다. 그 상태로 마이페이지에 3종 필수 규칙을 넣으면
 * "메일로 받은 비밀번호가 우리 정책 위반" 인 계정이 대량으로 생긴다.
 */
@DisplayName("PasswordGenerator")
class PasswordGeneratorTest {

    @Test
    @DisplayName("만들어낸 비밀번호는 언제나 비밀번호 정책을 통과한다")
    void alwaysSatisfiesPasswordPolicy() {
        // 시드를 고정해 재현 가능하게 둔다. 운영은 SecureRandom 을 쓴다.
        PasswordGenerator generator = new PasswordGenerator(new Random(20260814L));

        for (int i = 0; i < 1_000; i++) {
            String password = generator.generate();
            assertThat(PasswordPolicy.isSatisfiedBy(password))
                    .as("발급 비밀번호가 정책을 어겼다: %s", password)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("종류를 고정한 앞자리가 그대로 남지 않는다 — 섞어서 위치를 숨긴다")
    void shufflesSoCategoryPositionsAreNotFixed() {
        PasswordGenerator generator = new PasswordGenerator(new Random(1L));

        // 섞지 않으면 0번 자리가 항상 영문이다. 1000번 중 한 번이라도 아니면 섞이고 있는 것이다.
        boolean firstCharVaries = false;
        for (int i = 0; i < 1_000 && !firstCharVaries; i++) {
            firstCharVaries = !Character.isLetter(generator.generate().charAt(0));
        }

        assertThat(firstCharVaries).isTrue();
    }
}
