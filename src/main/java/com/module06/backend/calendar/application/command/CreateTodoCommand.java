package com.module06.backend.calendar.application.command;

import java.time.LocalDate;

// endDate는 nullable — 미지정 시 서비스 계층에서 date와 동일값으로 채운다(단일 날짜 하위호환).
public record CreateTodoCommand(Long companyId, Long memberId, String title, LocalDate date, LocalDate endDate) {
}
