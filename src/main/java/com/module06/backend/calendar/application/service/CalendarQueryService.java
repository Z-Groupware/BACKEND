package com.module06.backend.calendar.application.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.calendar.application.usecase.CalendarItem;
import com.module06.backend.calendar.application.usecase.CalendarItemType;
import com.module06.backend.calendar.application.usecase.GetCalendarUseCase;
import com.module06.backend.calendar.domain.model.PersonalTodo;
import com.module06.backend.calendar.domain.repository.PersonalTodoRepository;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    GET /api/calendar 통합조회 구현체. OWNER는 본인이 만든 프로젝트 기간만, LEADER·MEMBER는
    본인 개인 액션 마감일 + 개인 Todo만 본다(2026-08-06 Figma 확인, 팀 액션은 제외).
    project·action·calendar 셋 다 같은 담당자(BE PL) 소유라 ACL 포트 없이 도메인
    Repository를 직접 조합한다(2026-08-09 확인).

    연결된 클래스
    - ProjectRepository · ActionRepository · PersonalTodoRepository : 조회 대상
    - CalendarController                                            : 호출자 (presentation)
*/
@Service
@RequiredArgsConstructor
public class CalendarQueryService implements GetCalendarUseCase {

    private final ProjectRepository projectRepository;
    private final ActionRepository actionRepository;
    private final PersonalTodoRepository personalTodoRepository;

    @Override
    public List<CalendarItem> getCalendar(Long companyId, Long memberId, String authority, YearMonth month) {
        if ("OWNER".equals(authority)) {
            return getOwnerProjects(companyId, memberId, month);
        }
        return getPersonalDeadlines(companyId, memberId, month);
    }

    private List<CalendarItem> getOwnerProjects(Long companyId, Long memberId, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        return projectRepository.findAllByCompanyIdAndCreatedBy(companyId, memberId).stream()
                .filter(project -> overlapsMonth(project, monthStart, monthEnd))
                .map(this::toProjectItem)
                .toList();
    }

    private boolean overlapsMonth(Project project, LocalDate monthStart, LocalDate monthEnd) {
        LocalDate projectStart = project.getCreatedAt().toLocalDate();
        LocalDate projectEnd = project.getDueDate();
        return !projectEnd.isBefore(monthStart) && !projectStart.isAfter(monthEnd);
    }

    private CalendarItem toProjectItem(Project project) {
        return new CalendarItem(
                CalendarItemType.PROJECT,
                null,
                project.getName(),
                project.getTag(),
                project.getCreatedAt().toLocalDate(),
                project.getDueDate(),
                null
        );
    }

    private List<CalendarItem> getPersonalDeadlines(Long companyId, Long memberId, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        List<CalendarItem> actionItems = actionRepository.findAllByAssigneeMemberId(memberId).stream()
                .filter(action -> action.getCompanyId().equals(companyId))
                .filter(action -> action.getActionType() == ActionType.PERSONAL)
                .filter(action -> isWithin(action.getDueDate(), monthStart, monthEnd))
                .map(this::toActionItem)
                .toList();

        List<CalendarItem> todoItems = personalTodoRepository
                .findAllByMemberIdAndDateBetween(memberId, monthStart, monthEnd).stream()
                .map(this::toTodoItem)
                .toList();

        return Stream.concat(actionItems.stream(), todoItems.stream()).toList();
    }

    private boolean isWithin(LocalDate date, LocalDate monthStart, LocalDate monthEnd) {
        return date != null && !date.isBefore(monthStart) && !date.isAfter(monthEnd);
    }

    private CalendarItem toActionItem(Action action) {
        return new CalendarItem(
                CalendarItemType.ACTION,
                null,
                action.getTitle(),
                null,
                action.getDueDate(),
                action.getDueDate(),
                null
        );
    }

    private CalendarItem toTodoItem(PersonalTodo todo) {
        return new CalendarItem(
                CalendarItemType.TODO,
                todo.getId(),
                todo.getTitle(),
                null,
                todo.getDate(),
                todo.getDate(),
                todo.isDone()
        );
    }
}
