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

    // ---------- countChildActionProgressByParentActionIds (이슈 #355) ----------

    @Test
    void countChildActionProgressGroupsDoneAndTotalPerParentTeamAction() {
        Action parentA = actionRepository.save(team(ActionStatus.IN_PROGRESS));
        Action parentB = actionRepository.save(team(ActionStatus.IN_PROGRESS));
        actionRepository.save(childUnderParent(parentA.getId(), ActionStatus.DONE));
        actionRepository.save(childUnderParent(parentA.getId(), ActionStatus.TODO));
        actionRepository.save(childUnderParent(parentB.getId(), ActionStatus.DONE));

        var result = actionRepository.countChildActionProgressByParentActionIds(
                COMPANY, List.of(parentA.getId(), parentB.getId()));

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(progress -> {
            assertThat(progress.parentActionId()).isEqualTo(parentA.getId());
            assertThat(progress.totalCount()).isEqualTo(2);
            assertThat(progress.doneCount()).isEqualTo(1);
        });
        assertThat(result).anySatisfy(progress -> {
            assertThat(progress.parentActionId()).isEqualTo(parentB.getId());
            assertThat(progress.totalCount()).isEqualTo(1);
            assertThat(progress.doneCount()).isEqualTo(1);
        });
    }

    // CodeRabbit(#357) 지적 반영 — 다른 회사의 PERSONAL 액션이 우리 회사 팀 액션과 같은
    // parentActionId를 참조해도(같은 물리 테이블의 auto-increment id라 값 자체는 겹칠 수 있다)
    // companyId로 걸러져 집계에 안 잡히는지 확인한다.
    @Test
    void countChildActionProgressExcludesOtherCompanyChildActions() {
        Long otherCompany = 2L;
        Action parent = actionRepository.save(team(ActionStatus.IN_PROGRESS));
        actionRepository.save(childUnderParent(parent.getId(), ActionStatus.DONE));
        Action otherCompanyChild = Action.reconstitute(
                null, otherCompany, 1L, parent.getId(), null, null, ASSIGNEE, ActionType.PERSONAL,
                "다른 회사 개인 액션", "설명", true, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, true, null, null, null);
        actionRepository.save(otherCompanyChild);

        var result = actionRepository.countChildActionProgressByParentActionIds(COMPANY, List.of(parent.getId()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalCount()).isEqualTo(1);
        assertThat(result.get(0).doneCount()).isEqualTo(1);
    }

    @Test
    void countChildActionProgressOmitsParentWithNoChildrenYet() {
        Action parent = actionRepository.save(team(ActionStatus.TODO));

        var result = actionRepository.countChildActionProgressByParentActionIds(COMPANY, List.of(parent.getId()));

        assertThat(result).isEmpty();
    }

    @Test
    void countChildActionProgressReturnsEmptyWithoutQueryingWhenIdsIsEmpty() {
        assertThat(actionRepository.countChildActionProgressByParentActionIds(COMPANY, List.of())).isEmpty();
    }

    // Action.createManual은 parentActionId를 받지 않는다(항상 null 고정) — 하위 개인 액션은
    // team()처럼 reconstitute로 직접 만든다.
    private Action childUnderParent(Long parentActionId, ActionStatus status) {
        LocalDate startDate = status == ActionStatus.TODO ? null : LocalDate.of(2026, 8, 1);
        boolean isDone = status == ActionStatus.DONE;
        return Action.reconstitute(
                null, COMPANY, 1L, parentActionId, null, TEAM, ASSIGNEE, ActionType.PERSONAL, "하위 개인 액션", "설명",
                isDone, startDate, LocalDate.of(2026, 12, 31), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, true, null, null, null);
    }

    // ---------- countTeamMemberActionsByTeamId / countActionsByAssigneeMemberIds (이슈 #352) ----------

    // CodeRabbit 지적으로 발견 — 이전 fixture(personalUnderTeam)가 PERSONAL 액션에 teamId를
    // 직접 채워 넣었는데, 실제로는 ActionTypeShapePolicy.checkTeamShape가 "PERSONAL은 팀을 가질
    // 수 없다"고 막는 조합이라 프로덕션에 나올 수 없는 상태였다. reconstitute는 정책을 안 거쳐서
    // 그 비현실적인 fixture로도 테스트가 통과해버렸고, 그 뒤에 실제 카운트 쿼리가 항상 0을
    // 반환하는 버그가 숨어 있었다. 이제 실제 shape(PERSONAL은 teamId=null, parentActionId로만
    // 팀 액션과 연결)대로 만든다 — 저장된 TEAM 액션의 진짜 id를 부모로 쓴다.
    @Test
    void countTeamMemberActionsByTeamIdCountsPersonalActionsUnderThisTeamsTeamActions() {
        Action teamAction = actionRepository.save(team(ActionStatus.IN_PROGRESS));
        actionRepository.save(personalUnderTeamAction(teamAction.getId(), ActionStatus.TODO));
        actionRepository.save(personalUnderTeamAction(teamAction.getId(), ActionStatus.DONE));

        long result = actionRepository.countTeamMemberActionsByTeamId(TEAM);

        assertThat(result).isEqualTo(2L);
    }

    @Test
    void countTeamMemberActionsByTeamIdExcludesPersonalActionsUnderOtherTeams() {
        Long otherTeam = 99L;
        Action ourTeamAction = actionRepository.save(team(ActionStatus.IN_PROGRESS));
        Action otherTeamAction = Action.reconstitute(
                null, COMPANY, 1L, null, null, otherTeam, null, ActionType.TEAM, "다른 팀 액션", "설명",
                true, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, true, null, null, null);
        otherTeamAction = actionRepository.save(otherTeamAction);
        actionRepository.save(personalUnderTeamAction(ourTeamAction.getId(), ActionStatus.TODO));
        actionRepository.save(personalUnderTeamAction(otherTeamAction.getId(), ActionStatus.TODO));

        assertThat(actionRepository.countTeamMemberActionsByTeamId(TEAM)).isEqualTo(1L);
    }

    @Test
    void countTeamMemberActionsByTeamIdReturnsZeroWhenTeamHasNoTeamActions() {
        assertThat(actionRepository.countTeamMemberActionsByTeamId(TEAM)).isZero();
    }

    @Test
    void countActionsByAssigneeMemberIdsGroupsByAssignee() {
        Long other = 51L;
        actionRepository.save(personal(ActionStatus.TODO, LocalDate.of(2026, 12, 31))); // ASSIGNEE
        actionRepository.save(personal(ActionStatus.DONE, LocalDate.of(2026, 12, 31))); // ASSIGNEE
        actionRepository.save(Action.createManual(
                COMPANY, 1L, null, other, ActionType.PERSONAL, "제목", "설명", LocalDate.of(2026, 12, 31)));

        var result = actionRepository.countActionsByAssigneeMemberIds(List.of(ASSIGNEE, other));

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(count -> {
            assertThat(count.assigneeMemberId()).isEqualTo(ASSIGNEE);
            assertThat(count.actionCount()).isEqualTo(2L);
        });
        assertThat(result).anySatisfy(count -> {
            assertThat(count.assigneeMemberId()).isEqualTo(other);
            assertThat(count.actionCount()).isEqualTo(1L);
        });
    }

    @Test
    void countActionsByAssigneeMemberIdsReturnsEmptyWithoutQueryingWhenIdsIsEmpty() {
        assertThat(actionRepository.countActionsByAssigneeMemberIds(List.of())).isEmpty();
    }

    // PERSONAL 액션은 teamId를 못 가진다(ActionTypeShapePolicy.checkTeamShape) — 실제 shape대로
    // teamId=null, parentActionId만 부모 TEAM 액션의 진짜 id로 채운다.
    private Action personalUnderTeamAction(Long parentActionId, ActionStatus status) {
        LocalDate startDate = status == ActionStatus.TODO ? null : LocalDate.of(2026, 8, 1);
        boolean isDone = status == ActionStatus.DONE;
        return Action.reconstitute(
                null, COMPANY, 1L, parentActionId, null, null, ASSIGNEE, ActionType.PERSONAL, "개인 액션", "설명",
                isDone, startDate, LocalDate.of(2026, 12, 31), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, true, null, null, null);
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
