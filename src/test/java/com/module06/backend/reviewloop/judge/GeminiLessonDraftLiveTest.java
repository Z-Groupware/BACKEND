package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 반자동 · 교훈 초안 라이브(Gemini) — AI가 오판 의심 finding에 대해 교훈 노트 초안을 실제로 써주는지.
 * GEMINI_API_KEY 있을 때만 실행. 사람은 이 초안을 승인/수정만 하면 됨(타이핑 최소화).
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@ExtendWith(SkipOnProviderUnavailable.class)
class GeminiLessonDraftLiveTest {

    @Test
    @DisplayName("AI가 FALSE_POSITIVE 교훈 노트 초안을 생성한다")
    void draftsLessonNote() throws java.io.IOException {
        Finding finding = new Finding("ARCH_003a", Severity.MINOR, "architecture",
                "타 도메인 JpaEntity(CourseReferenceEntity)를 조회 목적으로 참조함",
                "GetMyQuizzesService.java", 42, Confidence.MEDIUM, FindingSource.JUDGE);

        Lesson draft = new GeminiLessonDraftAdapter().draft(finding, LessonKind.FALSE_POSITIVE);

        assertThat(draft.ruleId()).isEqualTo("ARCH_003a");
        assertThat(draft.kind()).isEqualTo(LessonKind.FALSE_POSITIVE);
        assertThat(draft.humanNote()).isNotBlank();   // AI가 노트 초안을 써줌

        // 데모: AI가 실제로 써준 초안을 파일로 남긴다(확인용)
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/gemini-lesson-draft.txt"),
                "[" + draft.kind() + "] " + draft.ruleId() + "\nAI 초안 노트: " + draft.humanNote());
    }
}
