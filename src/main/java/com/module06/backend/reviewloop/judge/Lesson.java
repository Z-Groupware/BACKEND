package com.module06.backend.reviewloop.judge;

/**
 * 지식 축적 루프의 최소 단위 — "AI 판단이 틀렸던 케이스와, 사람이 어떻게 정정했는지".
 * 이게 쌓여 다음 라운드 Judge 프롬프트에 주입된다(과거 실수 반복 방지 = 시스템이 학습).
 */
public record Lesson(
        String timestamp,
        String ruleId,
        LessonKind kind,
        String humanNote
) {}
