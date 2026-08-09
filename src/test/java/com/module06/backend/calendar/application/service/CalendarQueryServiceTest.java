package com.module06.backend.calendar.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.model.AssigneeSource;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.calendar.application.usecase.CalendarItem;
import com.module06.backend.calendar.application.usecase.CalendarItemType;
import com.module06.backend.calendar.domain.model.PersonalTodo;
import com.module06.backend.calendar.domain.repository.PersonalTodoRepository;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.domain.repository.ProjectRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarQueryServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long MEMBER = 5L;
    private static final YearMonth MONTH = YearMonth.of(2026, 8);

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private PersonalTodoRepository personalTodoRepository;

    private CalendarQueryService service() {
        return new CalendarQueryService(projectRepository, actionRepository, personalTodoRepository);
    }

    @Test
    void ownerSeesOnlyOwnProjectsOverlappingTheMonth() {
        Project overlapping = project(1L, "겹치는 프로젝트", LocalDateTime.of(2026, 7, 20, 0, 0), LocalDate.of(2026, 8, 10));
        Project notOverlapping = project(2L, "안 겹치는 프로젝트", LocalDateTime.of(2026, 5, 1, 0, 0), LocalDate.of(2026, 5, 31));
        when(projectRepository.findAllByCompanyIdAndCreatedBy(COMPANY, MEMBER))
                .thenReturn(List.of(overlapping, notOverlapping));

        List<CalendarItem> result = service().getCalendar(COMPANY, MEMBER, "OWNER", MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(CalendarItemType.PROJECT);
        assertThat(result.get(0).title()).isEqualTo("겹치는 프로젝트");
        assertThat(result.get(0).startDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(result.get(0).endDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void ownerDoesNotSeeActionsOrTodos() {
        when(projectRepository.findAllByCompanyIdAndCreatedBy(COMPANY, MEMBER)).thenReturn(List.of());

        service().getCalendar(COMPANY, MEMBER, "OWNER", MONTH);

        org.mockito.Mockito.verifyNoInteractions(actionRepository, personalTodoRepository);
    }

    @Test
    void memberSeesPersonalActionDeadlinesAndTodosCombined() {
        Action personalAction = action(1L, COMPANY, ActionType.PERSONAL, LocalDate.of(2026, 8, 15));
        when(actionRepository.findAllByAssigneeMemberId(MEMBER)).thenReturn(List.of(personalAction));
        PersonalTodo todo = PersonalTodo.reconstitute(
                10L, COMPANY, MEMBER, "우유 사기", LocalDate.of(2026, 8, 20), false, null, null);
        when(personalTodoRepository.findAllByMemberIdAndDateBetween(
                MEMBER, MONTH.atDay(1), MONTH.atEndOfMonth())).thenReturn(List.of(todo));

        List<CalendarItem> result = service().getCalendar(COMPANY, MEMBER, "MEMBER", MONTH);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CalendarItem::type)
                .containsExactlyInAnyOrder(CalendarItemType.ACTION, CalendarItemType.TODO);
    }

    @Test
    void leaderExcludesTeamActions() {
        Action teamAction = action(1L, COMPANY, ActionType.TEAM, LocalDate.of(2026, 8, 15));
        when(actionRepository.findAllByAssigneeMemberId(MEMBER)).thenReturn(List.of(teamAction));
        when(personalTodoRepository.findAllByMemberIdAndDateBetween(any(), any(), any())).thenReturn(List.of());

        List<CalendarItem> result = service().getCalendar(COMPANY, MEMBER, "LEADER", MONTH);

        assertThat(result).isEmpty();
    }

    @Test
    void excludesActionsFromAnotherCompany() {
        Action otherCompanyAction = action(1L, 999L, ActionType.PERSONAL, LocalDate.of(2026, 8, 15));
        when(actionRepository.findAllByAssigneeMemberId(MEMBER)).thenReturn(List.of(otherCompanyAction));
        when(personalTodoRepository.findAllByMemberIdAndDateBetween(any(), any(), any())).thenReturn(List.of());

        List<CalendarItem> result = service().getCalendar(COMPANY, MEMBER, "MEMBER", MONTH);

        assertThat(result).isEmpty();
    }

    @Test
    void excludesActionsWithDueDateOutsideMonth() {
        Action nextMonthAction = action(1L, COMPANY, ActionType.PERSONAL, LocalDate.of(2026, 9, 1));
        when(actionRepository.findAllByAssigneeMemberId(MEMBER)).thenReturn(List.of(nextMonthAction));
        when(personalTodoRepository.findAllByMemberIdAndDateBetween(any(), any(), any())).thenReturn(List.of());

        List<CalendarItem> result = service().getCalendar(COMPANY, MEMBER, "MEMBER", MONTH);

        assertThat(result).isEmpty();
    }

    private Project project(Long id, String name, LocalDateTime createdAt, LocalDate dueDate) {
        return Project.reconstitute(
                id, COMPANY, "TAG-" + id, name, "설명", "#FFFFFF",
                ProjectStatus.TODO, dueDate, MEMBER, List.of(),
                null, createdAt, createdAt);
    }

    private Action action(Long id, Long companyId, ActionType actionType, LocalDate dueDate) {
        return Action.reconstitute(
                id, companyId, null, null, null, null, MEMBER, actionType, "액션 " + id, "설명",
                false, LocalDate.of(2026, 8, 1), dueDate, false,
                ActionReviewStatus.HUMAN_CONFIRMED, AssigneeSource.EXPLICIT_CALL, null,
                null, true, null, LocalDateTime.now(), LocalDateTime.now());
    }
}
