package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate 2 · 환각 finding 차단 검증 — 근거(file:line)가 실재하는 finding만 채점에 넣는다.
 */
class EvidenceValidatorTest {

    @TempDir
    Path repoRoot;

    private EvidenceValidator validator;

    @BeforeEach
    void setUp() throws IOException {
        // 5줄짜리 파일 하나 준비
        Files.writeString(repoRoot.resolve("Real.java"), "1\n2\n3\n4\n5\n");
        validator = new EvidenceValidator(repoRoot);
    }

    private Finding at(String file, int line) {
        return new Finding("PERF_001", Severity.MINOR, "perf", "N+1", file, line, Confidence.MEDIUM);
    }

    @Test
    @DisplayName("실재 파일의 유효 라인 → 근거 있음")
    void realFileValidLineIsGrounded() {
        assertThat(validator.isGrounded(at("Real.java", 3))).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 파일 → 환각(근거 없음)")
    void missingFileIsNotGrounded() {
        assertThat(validator.isGrounded(at("Ghost.java", 1))).isFalse();
    }

    @Test
    @DisplayName("파일 라인 수를 넘는 라인 → 환각(근거 없음)")
    void lineBeyondEofIsNotGrounded() {
        assertThat(validator.isGrounded(at("Real.java", 99))).isFalse();
    }

    @Test
    @DisplayName("line<=0(파일 단위 지적)은 파일 존재만 확인")
    void fileScopedFindingChecksFileOnly() {
        assertThat(validator.isGrounded(at("Real.java", 0))).isTrue();
        assertThat(validator.isGrounded(at("Ghost.java", 0))).isFalse();
    }

    @Test
    @DisplayName("keepGrounded: 환각은 걸러내고 근거 있는 것만 남긴다")
    void keepGroundedFiltersHallucinations() {
        List<Finding> mixed = List.of(
                at("Real.java", 2),     // 근거 O
                at("Ghost.java", 1),    // 환각
                at("Real.java", 500));  // 라인 초과 환각
        List<Finding> kept = validator.keepGrounded(mixed);
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).line()).isEqualTo(2);
    }
}
