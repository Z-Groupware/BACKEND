package com.module06.backend.calendar.presentation.api.response;

import java.time.LocalDate;

import com.module06.backend.calendar.domain.model.PersonalTodo;

public record TodoResponse(Long id, String title, LocalDate date, LocalDate endDate, boolean isDone) {

    public static TodoResponse from(PersonalTodo todo) {
        return new TodoResponse(todo.getId(), todo.getTitle(), todo.getDate(), todo.getEndDate(), todo.isDone());
    }
}
