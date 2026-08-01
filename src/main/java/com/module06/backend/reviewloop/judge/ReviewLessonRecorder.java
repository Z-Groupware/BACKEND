package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Learning Loop 쓰기 끝 — 사람/드라이버의 판정을 교훈으로 기록한다.
 *
 * <p>드라이버(Claude Code)가 요청서의 finding을 사용자와 확정한 뒤 호출한다:
 * <ul>
 *   <li>사용자가 "건너뛰기(오탐)"로 판정 → {@code --kind FALSE_POSITIVE}
 *   <li>방안을 골라 수정함(=Judge 옳음) → {@code --kind CONFIRMED} (오탐률 분모)
 *   <li>Judge가 놓친 걸 사람이 뒤늦게 발견 → {@code --kind MISSED}
 * </ul>
 * 기록된 교훈은 다음 판정부터 {@link ReviewLoop}이 프롬프트에 실어 반영한다(루프가 닫힌다).
 *
 * <p>note는 두 방식:
 * <ul>
 *   <li>{@code --note '<텍스트>'} — 간단하지만 Windows에선 일부 문자(예: '—')가 argv 인코딩으로 깨질 수 있다.
 *   <li>{@code --note-file <경로>} — 파일을 UTF-8로 읽어 argv 인코딩을 우회한다(특수문자 안전).
 * </ul>
 *
 * <p>실행: {@code ./gradlew reviewLesson --args="--rule CONV_001 --kind FALSE_POSITIVE --note '재사용할 유틸이 없어 오탐'"}
 */
public final class ReviewLessonRecorder {

    public static void main(String[] args) throws IOException {
        Lesson lesson;
        try {
            lesson = parse(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.exit(2);
            return;
        }

        Files.createDirectories(ReviewLoopPaths.LESSONS.getParent());
        new KnowledgeStore(ReviewLoopPaths.LESSONS).record(lesson);

        System.out.println("[reviewLesson] 교훈 기록 → " + ReviewLoopPaths.LESSONS);
        System.out.println("  [" + lesson.kind() + "] " + lesson.ruleId() + " — " + lesson.humanNote());
        System.out.println("  다음 판정부터 프롬프트에 반영됩니다.");
    }

    /**
     * 인자를 교훈으로 파싱·검증한다. 잘못된 입력은 {@link IllegalArgumentException}(사용법 메시지).
     * rule·note는 빈/공백을 거른다 — 빈 ruleId가 lessons.jsonl·집계(RuleAccuracy)를 오염시키지 않게.
     */
    static Lesson parse(String[] args) throws IOException {
        String rule = CliArgs.value(args, "--rule", null);
        String kindArg = CliArgs.value(args, "--kind", null);
        String note = resolveNote(args);

        if (rule == null || rule.isBlank() || note == null || note.isBlank()) {
            throw new IllegalArgumentException(
                    "사용법: --args=\"--rule <RULE_ID> --kind <FALSE_POSITIVE|CONFIRMED|MISSED> "
                            + "(--note <한 줄 근거> | --note-file <UTF-8 경로>)\"");
        }
        LessonKind kind;
        try {
            kind = LessonKind.valueOf(kindArg == null ? "" : kindArg);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--kind 는 FALSE_POSITIVE, CONFIRMED, MISSED 중 하나여야 합니다: " + kindArg);
        }
        return new Lesson(LocalDateTime.now().toString(), rule.strip(), kind, note.strip());
    }

    /** note는 --note-file(UTF-8, argv 인코딩 우회) 우선, 없으면 --note(인라인). */
    private static String resolveNote(String[] args) throws IOException {
        String noteFile = CliArgs.value(args, "--note-file", null);
        if (noteFile != null && !noteFile.isBlank()) {
            return Files.readString(Path.of(noteFile.strip()), StandardCharsets.UTF_8);
        }
        return CliArgs.value(args, "--note", null);
    }

    private ReviewLessonRecorder() {
    }
}
