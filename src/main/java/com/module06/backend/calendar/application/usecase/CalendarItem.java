package com.module06.backend.calendar.application.usecase;

import java.time.LocalDate;

/* comment.
    GET /api/calendar 통합 응답 항목. PROJECT/ACTION/TODO 세 도메인의 서로 다른 shape을
    하나로 맞춘다 — 단일 날짜 항목(ACTION·TODO)은 startDate와 endDate가 같다.
*/
public record CalendarItem(
        CalendarItemType type,
        String title,
        String tag,
        LocalDate startDate,
        LocalDate endDate
) {
}
