package com.module06.backend.capture.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * meeting_summary 덮어쓰기 두 갈래.
 *
 * <p>검증의 축은 <b>편집 이력이 언제 사라져야 하는가</b>다. 개요 문장이 바뀌는 것은 화면에서
 * 바로 보이지만, edited_at 이 조용히 지워지면 <b>사람이 고친 요약이 「AI 원본 그대로」로
 * 보인다</b> — 데이터는 남고 그 데이터가 사람 손을 거쳤다는 사실만 사라진다.
 */
class MeetingSummaryOverwriteTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long EDITOR = 99L;

    @Test
    @DisplayName("⚠ 개요만 덮으면 편집 이력이 남는다 — OVERVIEW 계층이 사람 손을 지우면 안 된다")
    void 개요만_덮으면_편집_이력이_남는다() {
        MeetingSummaryJpaEntity summary = editedSummary();

        summary.overwriteOverview("배포 일정과 담당자를 확정했다.", "gemini-flash", "v1");

        assertThat(summary.getOverview()).isEqualTo("배포 일정과 담당자를 확정했다.");
        /*
         * 이 계층은 개요 한 문장만 바꾼다. 사람이 고친 **항목들은 meeting_decision 에 그대로
         * 남아 있으므로**, 여기서 이력을 지우면 화면이 그 요약을 AI 원본으로 표시한다.
         */
        assertThat(summary.getEditedAt()).isNotNull();
        assertThat(summary.getEditedByMemberId()).isEqualTo(EDITOR);
    }

    @Test
    @DisplayName("전체 교체는 편집 이력을 지운다 — 재분석은 사람이 고친 항목 자체를 새로 만든다")
    void 전체_교체는_편집_이력을_지운다() {
        MeetingSummaryJpaEntity summary = editedSummary();

        summary.overwrite("새 개요", "gemini-flash", "v1");

        /*
         * ANLZ-01 강제 재실행 경로다. 요약과 항목을 통째로 새로 만들므로 "사람이 고쳤다"가
         * 더 이상 참이 아니다 — 고쳤던 항목 자체가 지워지고 새 id 로 다시 만들어진다.
         * 두 메서드의 차이가 이것뿐이고, 그게 둘을 나눈 이유다.
         */
        assertThat(summary.getEditedAt()).isNull();
        assertThat(summary.getEditedByMemberId()).isNull();
    }

    private static MeetingSummaryJpaEntity editedSummary() {
        MeetingSummaryJpaEntity summary = MeetingSummaryJpaEntity.of(
                COMPANY, MEETING, "· 배포 일정\n(주제 요약)", "gemini-flash", "v1");
        summary.markEdited(EDITOR, LocalDateTime.of(2026, 8, 12, 10, 0));
        return summary;
    }
}
