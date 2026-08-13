package com.module06.backend.calendar.application.service;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.calendar.application.command.CreateTodoCommand;
import com.module06.backend.calendar.domain.model.PersonalTodo;
import com.module06.backend.calendar.domain.repository.PersonalTodoRepository;
import com.module06.backend.calendar.exception.CalendarErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalTodoServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long MEMBER = 5L;
    private static final Long TODO_ID = 10L;

    @Mock
    private PersonalTodoRepository personalTodoRepository;

    private PersonalTodoService service() {
        return new PersonalTodoService(personalTodoRepository);
    }

    @Test
    void createsTodoAsUndoneAndSaves() {
        PersonalTodoService service = service();
        when(personalTodoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalTodo result = service.create(
                new CreateTodoCommand(COMPANY, MEMBER, "우유 사기", LocalDate.of(2026, 8, 20), null));

        assertThat(result.getCompanyId()).isEqualTo(COMPANY);
        assertThat(result.getMemberId()).isEqualTo(MEMBER);
        assertThat(result.getTitle()).isEqualTo("우유 사기");
        assertThat(result.getDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(result.isDone()).isFalse();
    }

    @Test
    void endDateDefaultsToDateWhenOmitted() {
        PersonalTodoService service = service();
        when(personalTodoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalTodo result = service.create(
                new CreateTodoCommand(COMPANY, MEMBER, "우유 사기", LocalDate.of(2026, 8, 20), null));

        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void savesExplicitEndDateWhenProvided() {
        PersonalTodoService service = service();
        when(personalTodoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalTodo result = service.create(new CreateTodoCommand(
                COMPANY, MEMBER, "여행", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 25)));

        assertThat(result.getDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void createThrowsWhenEndDateIsBeforeDate() {
        PersonalTodoService service = service();

        assertThatThrownBy(() -> service.create(new CreateTodoCommand(
                COMPANY, MEMBER, "여행", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 19))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CalendarErrorCode.TODO_INVALID_DATE_RANGE);

        verify(personalTodoRepository, never()).save(any());
    }

    @Test
    void togglesUndoneTodoToDone() {
        PersonalTodoService service = service();
        PersonalTodo existing = PersonalTodo.reconstitute(
                TODO_ID, COMPANY, MEMBER, "우유 사기", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20), false, null, null);
        when(personalTodoRepository.findById(TODO_ID)).thenReturn(Optional.of(existing));
        when(personalTodoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalTodo result = service.toggleComplete(COMPANY, MEMBER, TODO_ID);

        assertThat(result.isDone()).isTrue();
    }

    @Test
    void togglesDoneTodoBackToUndone() {
        PersonalTodoService service = service();
        PersonalTodo existing = PersonalTodo.reconstitute(
                TODO_ID, COMPANY, MEMBER, "우유 사기", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20), true, null, null);
        when(personalTodoRepository.findById(TODO_ID)).thenReturn(Optional.of(existing));
        when(personalTodoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalTodo result = service.toggleComplete(COMPANY, MEMBER, TODO_ID);

        assertThat(result.isDone()).isFalse();
    }

    @Test
    void toggleThrowsWhenTodoDoesNotExist() {
        PersonalTodoService service = service();
        when(personalTodoRepository.findById(TODO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleComplete(COMPANY, MEMBER, TODO_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CalendarErrorCode.TODO_NOT_FOUND);
    }

    @Test
    void toggleThrowsWhenCallerIsNotTheOwner() {
        PersonalTodoService service = service();
        PersonalTodo ownedByAnother = PersonalTodo.reconstitute(
                TODO_ID, COMPANY, 999L, "남의 Todo", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20), false, null, null);
        when(personalTodoRepository.findById(TODO_ID)).thenReturn(Optional.of(ownedByAnother));

        assertThatThrownBy(() -> service.toggleComplete(COMPANY, MEMBER, TODO_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CalendarErrorCode.TODO_NOT_FOUND);

        verify(personalTodoRepository, never()).save(any());
    }

    @Test
    void toggleThrowsWhenTodoBelongsToAnotherCompany() {
        PersonalTodoService service = service();
        PersonalTodo otherCompanyTodo = PersonalTodo.reconstitute(
                TODO_ID, 999L, MEMBER, "다른 회사 Todo", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20), false, null, null);
        when(personalTodoRepository.findById(TODO_ID)).thenReturn(Optional.of(otherCompanyTodo));

        assertThatThrownBy(() -> service.toggleComplete(COMPANY, MEMBER, TODO_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CalendarErrorCode.TODO_NOT_FOUND);
    }
}
