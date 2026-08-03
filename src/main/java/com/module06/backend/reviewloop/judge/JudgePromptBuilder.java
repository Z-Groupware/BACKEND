package com.module06.backend.reviewloop.judge;

/**
 * rules.yaml에서 뽑은 Judge 규칙으로 정책 프롬프트를 조립한다(순수 코드, LLM 없음).
 * 결과 문자열이 ClaudeJudgeAdapter.review(..., policy)로 들어간다.
 * 규칙이 rules.yaml에서 바뀌면 프롬프트도 자동으로 따라간다 — SSOT 연결.
 */
public class JudgePromptBuilder {

    public String buildPolicy(RuleCatalog catalog) {
        return buildPolicy(catalog, java.util.List.of());
    }

    /**
     * 규칙 + NOTE + (지식 축적 루프의) 과거 교훈을 합쳐 정책 프롬프트를 만든다.
     * lessons가 쌓일수록 Judge는 과거 오판·누락을 참고 → 같은 실수 반복 방지(시스템이 학습).
     */
    public String buildPolicy(RuleCatalog catalog, java.util.List<Lesson> lessons) {
        StringBuilder sb = new StringBuilder();

        sb.append("다음 의미규칙만 검토한다. 위반은 finding으로 보고하고 ruleId를 정확히 붙인다:\n");
        for (RuleCatalog.JudgeRule rule : catalog.judgeRules()) {
            sb.append("- ").append(rule.id())
              .append(" [").append(rule.severity()).append("]: ")
              .append(rule.text()).append('\n');
        }

        if (!catalog.notes().isEmpty()) {
            sb.append("\n다음은 위반이 아니다(의도적 결정). 절대 flag하지 마라:\n");
            for (RuleCatalog.Note note : catalog.notes()) {
                sb.append("- ").append(note.id()).append(": ").append(note.text()).append('\n');
            }
        }

        if (lessons != null && !lessons.isEmpty()) {
            StringBuilder mistakes = new StringBuilder();
            for (Lesson lesson : lessons) {
                if (lesson.kind() == LessonKind.CONFIRMED) {
                    continue;   // CONFIRMED는 Judge의 실수가 아님 — 정확도 집계용이라 프롬프트에서 제외
                }
                mistakes.append("- [").append(lesson.kind()).append("] ")
                        .append(lesson.ruleId()).append(": ").append(lesson.humanNote()).append('\n');
            }
            if (mistakes.length() > 0) {
                sb.append("\n과거 사람 검토에서 나온 교훈(같은 실수를 반복하지 마라):\n").append(mistakes);
            }
        }

        return sb.toString();
    }
}
