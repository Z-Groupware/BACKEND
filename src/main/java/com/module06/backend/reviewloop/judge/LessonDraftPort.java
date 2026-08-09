package com.module06.backend.reviewloop.judge;

/**
 * 교훈 초안 생성 seam(반자동) — finding + 사람이 고른 kind로 교훈 노트 '초안'을 제안한다.
 * 사람은 이 초안을 승인/수정만 하면 된다(타이핑 최소화). 최종 판단·veto는 사람 몫(HITL 유지).
 * 테스트=stub, 런타임=GeminiLessonDraftAdapter.
 */
@FunctionalInterface
public interface LessonDraftPort {
    Lesson draft(Finding finding, LessonKind kind);
}
