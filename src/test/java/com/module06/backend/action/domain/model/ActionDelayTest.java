package com.module06.backend.action.domain.model;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Action.isDelayed — 지연 배지 판정.
 *
 * <p>전에는 같은 식이 BE 안 세 곳에 흩어져 있었고 그중 ProjectService.getTimeline 만
 * {@code status != DONE} 이라, 마감 지난 TODO 팀 액션이 액션 목록에선 지연 아님으로,
 * 프로젝트 타임라인에선 지연으로 <b>동시에</b> 내려갔다. 판정식을 여기 하나로 모았으므로
 * 확정 규칙(WORKFLOW.md:37 "진행중 칸 안의 배지")을 이 테스트가 지킨다.
 *
 * <p>ActionPersistenceAdapter 의 overdue Specification 은 SQL 로 나가야 해서 이 메서드를
 * 부르지 못한다 — 판정식이 두 벌인 것은 구조상 불가피하고, 두 벌이 같은 답을 내는지는
 * 여기 표와 그쪽 조건식을 대조해 지킨다.
 */
@DisplayName("Action.isDelayed — 지연 판정")
class ActionDelayTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Test
    @DisplayName("진행중이면서 마감일이 지났으면 지연이다")
    void 진행중_마감초과는_지연이다() {
        assertThat(Action.isDelayed(ActionStatus.IN_PROGRESS, YESTERDAY, TODAY)).isTrue();
    }

    @Test
    @DisplayName("할일은 마감일이 지나도 지연이 아니다 — 이번 변경으로 값이 바뀌는 유일한 경우")
    void 할일은_마감초과여도_지연이_아니다() {
        // ProjectService.getTimeline 이 status != DONE 으로 판정하던 시절엔 이게 true 였다.
        // 확정 규칙은 "진행중 칸 안의 배지"라 할일은 아직 안 늦은 것이다.
        assertThat(Action.isDelayed(ActionStatus.TODO, YESTERDAY, TODAY)).isFalse();
    }

    @Test
    @DisplayName("완료는 마감일이 지나도 지연이 아니다")
    void 완료는_마감초과여도_지연이_아니다() {
        assertThat(Action.isDelayed(ActionStatus.DONE, YESTERDAY, TODAY)).isFalse();
    }

    @Test
    @DisplayName("마감일 당일은 지연이 아니다 — 당일까지는 안 늦은 것이다")
    void 마감일_당일은_지연이_아니다() {
        assertThat(Action.isDelayed(ActionStatus.IN_PROGRESS, TODAY, TODAY)).isFalse();
    }

    @Test
    @DisplayName("마감일이 남았으면 지연이 아니다")
    void 마감일이_남았으면_지연이_아니다() {
        assertThat(Action.isDelayed(ActionStatus.IN_PROGRESS, TOMORROW, TODAY)).isFalse();
    }

    @Test
    @DisplayName("마감일이 없으면 지연이 아니다 — 조회 경로의 record 는 null 을 실어 올 수 있다")
    void 마감일이_없으면_지연이_아니다() {
        // action.due_date 는 NOT NULL 이지만 ActionQueryPort.TeamActionSummary.dueDate 는
        // nullable 이다. 이 가드를 지우면 그 경로에서 NPE 가 난다.
        assertThat(Action.isDelayed(ActionStatus.IN_PROGRESS, null, TODAY)).isFalse();
    }
}
