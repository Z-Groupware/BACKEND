package com.module06.backend.action.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Action.applyHumanReview — RVW-02 가 부르는 상태 전이.
 *
 * <p>여기 검증의 핵심은 <b>확정 시각과 검토 상태가 어긋난 행을 저장하지 않는 것</b>이다. 그
 * 조합은 확정 집계와 반려 목록에 같은 액션을 동시에 올려 두 숫자를 서로 안 맞게 만든다.
 */
class ActionHumanReviewTest {

    private static final long ALICE = 42L;
    private static final long BOB = 43L;

    @Test
    @DisplayName("반려는 이전 확정 시각을 지운다 — 확정 상태와 확정 시각이 어긋난 행을 남기지 않는다")
    void 반려는_이전_확정_시각을_지운다() {
        // 이미 사람이 확정한 액션이다. 사람이 마음을 바꿔 뒤늦게 반려하는 경로가 있다.
        Action action = confirmedAction();
        assertThat(action.getConfirmedAt()).isNotNull();

        action.applyHumanReview(null, null, ActionReviewStatus.REJECTED);

        assertThat(action.getReviewStatus()).isEqualTo(ActionReviewStatus.REJECTED);
        assertThat(action.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("확정은 시각을 찍는다")
    void 확정은_시각을_찍는다() {
        Action action = pendingAction();

        action.applyHumanReview(null, null, ActionReviewStatus.HUMAN_CONFIRMED);

        assertThat(action.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("기한을 고치면 dueDateDefaulted 를 내린다 — 프로젝트 마감일로 채운 값이 아니게 된다")
    void 기한을_고치면_기본값_표시를_내린다() {
        Action action = pendingAction();
        assertThat(action.isDueDateDefaulted()).isTrue();

        action.applyHumanReview(BOB, LocalDate.of(2026, 8, 20), ActionReviewStatus.HUMAN_CONFIRMED);

        assertThat(action.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(action.isDueDateDefaulted()).isFalse();
        assertThat(action.getAssigneeMemberId()).isEqualTo(BOB);
    }

    @Test
    @DisplayName("담당자·기한이 null 이면 그대로 둔다 — null 은 '안 고쳤다'이고 '비우라'가 아니다")
    void null_은_값을_비우지_않는다() {
        Action action = pendingAction();

        action.applyHumanReview(null, null, ActionReviewStatus.HUMAN_CONFIRMED);

        assertThat(action.getAssigneeMemberId()).isEqualTo(ALICE);
        assertThat(action.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 8));
        // 기한을 고치지 않았으므로 기본값 표시도 그대로다.
        assertThat(action.isDueDateDefaulted()).isTrue();
    }

    private static Action pendingAction() {
        return action(ActionReviewStatus.PENDING, null);
    }

    private static Action confirmedAction() {
        return action(ActionReviewStatus.HUMAN_CONFIRMED, LocalDateTime.of(2026, 8, 5, 10, 0));
    }

    private static Action action(ActionReviewStatus reviewStatus, LocalDateTime confirmedAt) {
        return Action.reconstitute(
                8821L, 7L, 3L, 8800L, 900L, 5L, ALICE,
                ActionType.PERSONAL, "로드맵 초안 작성", null, false, null,
                LocalDate.of(2026, 8, 8), true, reviewStatus, AssigneeSource.EXPLICIT_CALL,
                8812L, null, false, confirmedAt, null, null);
    }
}
