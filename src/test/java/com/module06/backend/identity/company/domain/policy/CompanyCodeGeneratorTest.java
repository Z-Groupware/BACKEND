package com.module06.backend.identity.company.domain.policy;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompanyCodeGenerator")
class CompanyCodeGeneratorTest {

    @Test
    @DisplayName("XXXX-XXXX 형태 9자를 만든다 — 기존 코드 NOVA-7K3D 와 모양이 같아야 화면·메일이 그대로 간다")
    void generatesFourDashFourFormat() {
        CompanyCodeGenerator generator = new CompanyCodeGenerator(new SecureRandom());

        String code = generator.generate();

        assertThat(code).hasSize(9);
        assertThat(code.charAt(4)).isEqualTo('-');
        assertThat(code).matches("[0-9A-Z]{4}-[0-9A-Z]{4}");
    }

    @Test
    @DisplayName("혼동 문자 I·L·O·U 를 쓰지 않는다 — 사람이 메일로 받아 손으로 입력하는 값이다")
    void usesOnlyUnambiguousCharacters() {
        CompanyCodeGenerator generator = new CompanyCodeGenerator(new SecureRandom());

        Set<Character> used = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            for (char c : generator.generate().replace("-", "").toCharArray()) {
                used.add(c);
            }
        }

        assertThat(used).doesNotContain('I', 'L', 'O', 'U');
        assertThat(used).allSatisfy(c -> assertThat("0123456789ABCDEFGHJKMNPQRSTVWXYZ").contains(String.valueOf(c)));
    }

    @Test
    @DisplayName("여덟 글자 전부를 무작위 원천에서 뽑는다 — 한 글자라도 고정·유래 값이면 추측 범위가 줄어든다")
    void drawsEveryCharacterFromTheRandomSource() {
        CompanyCodeGenerator generator = new CompanyCodeGenerator(new SequenceRandom(0, 1, 2, 3, 4, 5, 6, 7));

        assertThat(generator.generate()).isEqualTo("0123-4567");
    }

    @Test
    @DisplayName("알파벳의 마지막 인덱스도 그대로 매핑한다 — 상한이 잘리면 실제 경우의 수가 준다")
    void mapsTheHighestAlphabetIndex() {
        CompanyCodeGenerator generator = new CompanyCodeGenerator(new SequenceRandom(31, 31, 31, 31, 0, 0, 0, 0));

        assertThat(generator.generate()).isEqualTo("ZZZZ-0000");
    }

    @Test
    @DisplayName("무작위 원천에 알파벳 크기 32 를 정확히 요구한다 — 더 큰 값을 나머지 연산하면 앞쪽 문자가 더 자주 나온다")
    void asksTheRandomSourceForExactlyTheAlphabetSize() {
        RecordingRandom random = new RecordingRandom();

        new CompanyCodeGenerator(random).generate();

        assertThat(random.requestedBounds).containsOnly(32);
        assertThat(random.requestedBounds).hasSize(8);
    }

    @Test
    @DisplayName("secure() 가 주는 생성기는 SecureRandom 을 원천으로 쓴다 — java.util.Random 이면 코드 하나로 전후 발급분이 계산된다")
    void secureFactoryUsesSecureRandom() throws Exception {
        java.lang.reflect.Field source = CompanyCodeGenerator.class.getDeclaredField("random");
        source.setAccessible(true);

        assertThat(source.get(CompanyCodeGenerator.secure())).isInstanceOf(SecureRandom.class);
    }

    /** 인덱스를 정해진 순서대로 돌려준다. 나머지 연산을 하지 않아 상한 초과 요구가 테스트에서 드러난다. */
    private static final class SequenceRandom implements RandomGenerator {
        private final int[] values;
        private int cursor;

        private SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return values[cursor++];
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException("이 스텁은 nextInt(bound) 만 쓴다");
        }
    }

    private static final class RecordingRandom implements RandomGenerator {
        private final java.util.List<Integer> requestedBounds = new java.util.ArrayList<>();

        @Override
        public int nextInt(int bound) {
            requestedBounds.add(bound);
            return 0;
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException("이 스텁은 nextInt(bound) 만 쓴다");
        }
    }
}
