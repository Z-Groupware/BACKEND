package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * findings 출력 방어 + 근거 검증 경로 규약.
 * 게이트가 자기 IO 오류로 push를 막으면 안 되고, 실재하는 finding을 환각으로 버려서도 안 된다.
 * LLM·판정은 여기 필요 없다 — 파일 쓰기·경로 포맷·EvidenceValidator 기준점만 본다.
 */
class ReviewLoopRunnerTest {

    @TempDir
    Path dir;

    private static Finding at(String file, int line) {
        return new Finding("CONV_001", Severity.MINOR, "convention", "설명",
                file, line, Confidence.HIGH);
    }

    @Test
    @DisplayName("부모 디렉터리가 없어도 findings를 쓴다 (NoSuchFileException 금지)")
    void createsMissingParentDirectories() throws IOException {
        Path out = dir.resolve("nope/deep/findings.txt");   // 부모 2단계가 없음

        ReviewLoopRunner.writeFindings(out.toString(), List.of("A.java:1 [CONV_001] 설명"));

        assertThat(out).exists();
        assertThat(Files.readString(out)).isEqualTo("A.java:1 [CONV_001] 설명\n");
    }

    @Test
    @DisplayName("파일명만 준 경우(부모 null) NPE 없이 쓴다")
    void writesWhenPathHasNoParent() throws IOException {
        // 부모 null 브랜치는 '파일명만' 있는 경로에서만 재현되므로 @TempDir을 쓸 수 없다(CWD에 쓸 수밖에 없음).
        // 대신 파일명에 UUID를 넣어 병렬 실행 시 다른 테스트와 충돌하지 않게 한다.
        Path bare = Path.of("reviewloop-findings-test-" + UUID.randomUUID() + ".txt");
        assertThat(bare.getParent()).isNull();   // 가드 없으면 createDirectories(null) → NPE

        try {
            assertThatCode(() -> ReviewLoopRunner.writeFindings(bare.toString(), List.of()))
                    .doesNotThrowAnyException();
            assertThat(bare).exists();
        } finally {
            Files.deleteIfExists(bare);
        }
    }

    @Test
    @DisplayName("findings 0건이어도 빈 파일을 남긴다")
    void writesEmptyFileWhenNoFindings() throws IOException {
        Path out = dir.resolve("empty/findings.txt");

        ReviewLoopRunner.writeFindings(out.toString(), List.of());

        assertThat(out).exists();
        assertThat(Files.readString(out)).isEmpty();
    }

    @Test
    @DisplayName("findings 경로는 OS 무관하게 '/' 로 정규화된다")
    void normalizesPathSeparatorToSlash() {
        Path osSpecific = Path.of("src", "main", "java", "A.java");   // OS 구분자로 조립

        String posix = ReviewLoopRunner.toPosixPath(osSpecific);

        assertThat(posix).isEqualTo("src/main/java/A.java");
        assertThat(posix).doesNotContain("\\");
    }

    // ── 경로 규약(P2 이식) — 근거 검증은 repo 루트가 아니라 '그 파일의 부모 디렉터리' 기준 ──

    @Test
    @DisplayName("깊은/worktree 경로에서도 파일명 기준 finding이 근거 있음으로 남는다")
    void groundsFindingRelativeToFileParent() throws IOException {
        // repo 루트 기준이라면 'A.java'는 루트에 없으니 환각으로 버려진다 — 부모 기준이라 살아야 한다.
        Path nested = Files.createDirectories(dir.resolve("wt/src/main/java/pkg"));
        Files.writeString(nested.resolve("A.java"), "class A {}\n");

        EvidenceValidator ev = ReviewLoopRunner.evidenceFor(nested.resolve("A.java"));

        assertThat(ev.isGrounded(at("A.java", 1))).isTrue();
        assertThat(ev.isGrounded(at("A.java", 0))).isTrue();      // line<=0 = 파일 단위 지적
    }

    @Test
    @DisplayName("실재하지 않는 파일·라인 초과는 여전히 환각으로 버린다")
    void stillRejectsHallucinations() throws IOException {
        Path nested = Files.createDirectories(dir.resolve("pkg"));
        Files.writeString(nested.resolve("A.java"), "class A {}\n");   // 1줄

        EvidenceValidator ev = ReviewLoopRunner.evidenceFor(nested.resolve("A.java"));

        assertThat(ev.isGrounded(at("A.java", 2))).isFalse();     // 라인 수 초과
        assertThat(ev.isGrounded(at("Ghost.java", 1))).isFalse(); // 없는 파일
    }

    @Test
    @DisplayName("부모 없는 경로(파일명만)는 CWD 기준 — NPE 없이 동작한다")
    void fallsBackToCwdWhenNoParent() {
        Path bare = Path.of("A.java");
        assertThat(bare.getParent()).isNull();

        assertThatCode(() -> ReviewLoopRunner.evidenceFor(bare).isGrounded(at("A.java", 1)))
                .doesNotThrowAnyException();
    }

    // ── 게이트 상태 신호 — 훅이 "코드 판정(BLOCKED)"과 "리뷰 미수행(ERROR)"을 구분하는 유일한 근거 ──

    @Test
    @DisplayName("상태 파일은 부모 디렉터리가 없어도 기록된다")
    void writesStatusCreatingParents() throws IOException {
        Path out = dir.resolve("nope/deep/status.txt");

        ReviewLoopRunner.writeStatus(out.toString(), ReviewLoopRunner.STATUS_BLOCKED);

        // 훅은 공백·개행을 떼고 정확히 일치를 본다 → 값에 잡것이 섞이면 안 된다.
        assertThat(Files.readString(out).trim()).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("세 상태는 서로 다른 값이다 (훅의 case 분기가 갈리는 근거)")
    void statusValuesAreDistinct() {
        assertThat(List.of(ReviewLoopRunner.STATUS_OK,
                        ReviewLoopRunner.STATUS_BLOCKED,
                        ReviewLoopRunner.STATUS_ERROR))
                .containsExactly("OK", "BLOCKED", "ERROR")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("상태 기록 실패는 예외를 던지지 않는다 — 신호 실패가 게이트를 망가뜨리면 안 된다")
    void statusWriteFailureIsSwallowed() throws IOException {
        // 파일을 부모로 지정 → createDirectories/writeString이 실패하는 경로
        Path file = Files.writeString(dir.resolve("blocker"), "x");
        Path impossible = file.resolve("child/status.txt");

        assertThatCode(() -> ReviewLoopRunner.writeStatus(impossible.toString(),
                ReviewLoopRunner.STATUS_ERROR)).doesNotThrowAnyException();
    }
}
