package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** reviewLesson 인자 파싱·검증 — 빈/잘못된 값 차단 + note 파일 입력(인코딩 안전). */
class ReviewLessonRecorderTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("정상 인자는 교훈으로 파싱된다 (rule·note는 trim)")
    void parsesValidArgs() throws IOException {
        Lesson lesson = ReviewLessonRecorder.parse(new String[]{
                "--rule", " CONV_001 ", "--kind", "CONFIRMED", "--note", " 실제 중복 확정 "});

        assertThat(lesson.ruleId()).isEqualTo("CONV_001");
        assertThat(lesson.kind()).isEqualTo(LessonKind.CONFIRMED);
        assertThat(lesson.humanNote()).isEqualTo("실제 중복 확정");
    }

    @Test
    @DisplayName("--note-file은 UTF-8로 읽어 특수문자(—)를 보존한다 (argv 인코딩 우회)")
    void noteFilePreservesSpecialChars() throws IOException {
        Path noteFile = dir.resolve("note.txt");
        Files.writeString(noteFile, "toPosixPath — 단일 사용 헬퍼", StandardCharsets.UTF_8);

        Lesson lesson = ReviewLessonRecorder.parse(new String[]{
                "--rule", "CONV_001", "--kind", "FALSE_POSITIVE", "--note-file", noteFile.toString()});

        assertThat(lesson.humanNote()).isEqualTo("toPosixPath — 단일 사용 헬퍼");
    }

    @Test
    @DisplayName("빈 rule은 거부한다 (빈 ruleId 기록 방지)")
    void rejectsBlankRule() {
        assertThatThrownBy(() -> ReviewLessonRecorder.parse(new String[]{
                "--rule", "  ", "--kind", "FALSE_POSITIVE", "--note", "x"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 note는 거부한다")
    void rejectsBlankNote() {
        assertThatThrownBy(() -> ReviewLessonRecorder.parse(new String[]{
                "--rule", "CONV_001", "--kind", "FALSE_POSITIVE", "--note", "   "}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CONFIRMED도 유효한 kind다")
    void acceptsConfirmedKind() throws IOException {
        Lesson lesson = ReviewLessonRecorder.parse(new String[]{
                "--rule", "CONV_001", "--kind", "CONFIRMED", "--note", "x"});
        assertThat(lesson.kind()).isEqualTo(LessonKind.CONFIRMED);
    }

    @Test
    @DisplayName("잘못된 kind는 거부한다")
    void rejectsUnknownKind() {
        assertThatThrownBy(() -> ReviewLessonRecorder.parse(new String[]{
                "--rule", "CONV_001", "--kind", "BOGUS", "--note", "x"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FALSE_POSITIVE");
    }
}
