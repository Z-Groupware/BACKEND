package com.module06.backend.calendar.application.command;

import java.time.LocalDate;

public record CreateTodoCommand(Long companyId, Long memberId, String title, LocalDate date) {
}
