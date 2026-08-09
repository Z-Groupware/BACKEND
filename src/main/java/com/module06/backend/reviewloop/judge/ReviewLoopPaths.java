package com.module06.backend.reviewloop.judge;

import java.nio.file.Path;

/**
 * 리뷰 루프 산출물 경로 — 성격에 따라 두 곳으로 나눈다(SSOT).
 *
 * <ul>
 *   <li>{@code logs/}      : 휴발성 감사 로그·데모. 실행마다 쌓이고 머신마다 다르다 → {@code .gitignore}로 무시.
 *   <li>{@code knowledge/} : 사람 정정 교훈. 영속·공유해야 Learning Loop가 팀·CI 차원에서 학습한다 → 추적(커밋).
 * </ul>
 *
 * 예전엔 lessons.jsonl이 무시되는 {@code logs/} 아래 있어 교훈이 로컬 한정이었다(팀·CI 미공유).
 */
final class ReviewLoopPaths {

    /** 사람 정정 교훈 — 추적(커밋) 대상. Learning Loop의 공유 기억. */
    static final Path LESSONS = Path.of("review-loop/knowledge/lessons.jsonl");

    /** 라운드 감사 로그 — 휴발성, 무시(.gitignore logs/). */
    static final Path AUDIT_LOG = Path.of("review-loop/logs/error_log.jsonl");

    private ReviewLoopPaths() {
    }
}
