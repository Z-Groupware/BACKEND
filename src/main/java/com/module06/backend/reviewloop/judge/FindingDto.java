package com.module06.backend.reviewloop.judge;

/**
 * LLM structured output용 DTO(문자열 필드). SDK가 이 레코드로 JSON 스키마를 유도한다.
 * 도메인 enum(Severity/Confidence)은 LLM 응답 파싱 후 ClaudeJudgeAdapter에서 매핑한다.
 */
public record FindingDto(
        String ruleId,
        String severity,     // "CRITICAL" | "MINOR"
        String category,
        String description,
        String file,
        int line,
        String confidence    // "LOW" | "MEDIUM" | "HIGH"
) {}
