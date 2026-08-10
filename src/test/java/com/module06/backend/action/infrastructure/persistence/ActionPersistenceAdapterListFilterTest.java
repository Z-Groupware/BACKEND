package com.module06.backend.action.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/* comment.
    2026-08-10 목록 필터/정렬(이홍근 요청) 실 SQL 정합성 테스트. Specification으로 만든 동적
    조건이라 가짜 리포지터리로는 검증이 안 된다 — 실제 DB 위에서 status·overdue 필터와
    dueDate 정렬이 맞게 동작하는지 확인한다.
*/
@SpringBootTest
@Transactional
class ActionPersistenceAdapterListFilterTest {

    private static final Long COMPANY = 1L;
    private static final Long ASSIGNEE = 50L;
    private static final Long TEAM = 70L;

    @Autowired
    private ActionRepository actionRepository;

    @Test
    void filtersPersonalActionsByStatus() {
        actionRepository.save(personal(ActionStatus.TODO, LocalDate.of(2026, 12, 31)));
        actionRepository.save(personal(ActionStatus.IN_PROGRESS, LocalDate.of(2026, 12, 31)));
        actionRepository.save(personal(ActionStatus.DONE, LocalDate.of(2026, 12, 31)));

        List<Action> result = actionRepository.findAllByAssigneeMemberId(
                ASSIGNEE, ActionStatus.IN_PROGRESS, null, null, "desc", 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ActionStatus.IN_PROGRESS);
        assertThat(actionRepository.countByAssigneeMemberId(ASSIGNEE, ActionStatus.IN_PROGRESS, null)).isEqualTo(1L);
    }

    @Test
    void overdueTrueReturnsOnlyInProgressPastDueDate() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate future = LocalDate.now().plusDays(30);
        actionRepository.save(personal(ActionStatus.IN_PROGRESS, yesterday)); // 지연
        actionRepository.save(personal(ActionStatus.IN_PROGRESS, future)); // 안 지연
        actionRepository.save(personal(ActionStatus.TODO, yesterday)); // 지연 아님(할일은 지연 배지 대상 아님)

        List<Action> overdue = actionRepository.findAllByAssigneeMemberId(
                ASSIGNEE, null, true, null, "desc", 0, 20);
        List<Action> notOverdue = actionRepository.findAllByAssigneeMemberId(
                ASSIGNEE, null, false, null, "desc", 0, 20);

        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).getDueDate()).isEqualTo(yesterday);
        assertThat(notOverdue).hasSize(2);
    }

    @Test
    void sortsByDueDateAscending() {
        actionRepository.save(personal(ActionStatus.TODO, LocalDate.of(2026, 12, 31)));
        actionRepository.save(personal(ActionStatus.TODO, LocalDate.of(2026, 1, 1)));
        actionRepository.save(personal(ActionStatus.TODO, LocalDate.of(2026, 6, 15)));

        List<Action> result = actionRepository.findAllByAssigneeMemberId(
                ASSIGNEE, null, null, "dueDate", "asc", 0, 20);

        assertThat(result).extracting(Action::getDueDate).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 31));
    }

    @Test
    void filtersTeamActionsByStatus() {
        actionRepository.save(team(ActionStatus.TODO));
        actionRepository.save(team(ActionStatus.IN_PROGRESS));

        List<Action> result = actionRepository.findAllByTeamId(TEAM, ActionStatus.IN_PROGRESS, null, "desc", 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ActionStatus.IN_PROGRESS);
        assertThat(actionRepository.countByTeamId(TEAM, ActionStatus.IN_PROGRESS)).isEqualTo(1L);
    }

    private Action personal(ActionStatus status, LocalDate dueDate) {
        Action action = Action.createManual(
                COMPANY, 1L, null, ASSIGNEE, ActionType.PERSONAL, "제목", "설명", dueDate);
        applyStatus(action, status);
        return action;
    }

    // reconstitute는 status를 직접 받지 않는다 — isDone·startDate로부터 파생되므로(2026-08-07
    // 재설계), TODO를 만들려면 startDate=null, IN_PROGRESS는 startDate를 채워서 만든다.
    private Action team(ActionStatus status) {
        LocalDate startDate = status == ActionStatus.TODO ? null : LocalDate.of(2026, 8, 1);
        boolean isDone = status == ActionStatus.DONE;
        return Action.reconstitute(
                null, COMPANY, 1L, null, null, TEAM, null, ActionType.TEAM, "팀 액션", "설명",
                isDone, startDate, LocalDate.of(2026, 12, 31), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, true, null, null, null);
    }

    // 상태 전이 메서드가 진행중/완료만 공개돼있어(할일→진행중, 진행중→완료), 테스트 데이터는
    // 실제 유스케이스 흐름대로 전이시켜서 만든다 — reconstitute로 임의 상태를 바로 박지 않는다.
    private void applyStatus(Action action, ActionStatus status) {
        if (status == ActionStatus.IN_PROGRESS || status == ActionStatus.DONE) {
            action.start();
        }
        if (status == ActionStatus.DONE) {
            action.complete();
        }
    }
}
