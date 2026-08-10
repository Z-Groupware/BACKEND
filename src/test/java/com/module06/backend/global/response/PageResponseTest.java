package com.module06.backend.global.response;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/* comment.
    hasNext 계산의 int 오버플로 회귀 테스트(CodeRabbit 지적, PR #305) — page가
    Integer.MAX_VALUE여도 (page+1)이 int로 먼저 계산돼 음수로 뒤집히면 안 된다.
*/
class PageResponseTest {

    @Test
    void hasNextDoesNotOverflowAtMaxIntPage() {
        PageResponse<String> result = PageResponse.of(List.of(), Integer.MAX_VALUE, 20, 100L);

        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void hasNextIsTrueWhenMoreRowsRemain() {
        PageResponse<String> result = PageResponse.of(List.of("a"), 0, 5, 42L);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.totalPages()).isEqualTo(9);
    }

    @Test
    void hasNextIsFalseOnLastPage() {
        PageResponse<String> result = PageResponse.of(List.of("a"), 8, 5, 42L);

        assertThat(result.hasNext()).isFalse();
    }
}
