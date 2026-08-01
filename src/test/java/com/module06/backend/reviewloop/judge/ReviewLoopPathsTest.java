package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 경로 성격 회귀 방지 — 교훈은 추적(공유) 위치, 감사 로그는 무시(휴발) 위치.
 * lessons가 다시 logs/ 아래로 돌아가면 .gitignore에 걸려 팀·CI 미공유로 회귀한다.
 */
class ReviewLoopPathsTest {

    private String posix(java.nio.file.Path p) {
        return p.toString().replace('\\', '/');
    }

    @Test
    @DisplayName("lessons는 추적되는 knowledge/ 아래에 있고, 무시되는 logs/ 아래가 아니다")
    void lessonsLiveInTrackedKnowledgeDir() {
        assertThat(posix(ReviewLoopPaths.LESSONS))
                .contains("review-loop/knowledge/")
                .doesNotContain("/logs/");
    }

    @Test
    @DisplayName("감사 로그는 무시되는 logs/ 아래에 있다(휴발성)")
    void auditLogLivesInIgnoredLogsDir() {
        assertThat(posix(ReviewLoopPaths.AUDIT_LOG)).contains("review-loop/logs/");
    }
}
