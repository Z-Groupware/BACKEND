package com.module06.backend.calendar.presentation.api.response;

import java.time.LocalDate;

import com.module06.backend.calendar.application.usecase.CalendarItem;
import com.module06.backend.calendar.application.usecase.CalendarItemType;

public record CalendarItemResponse(
        CalendarItemType type,
        Long id,
        String title,
        String tag,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isDone
) {

    public static CalendarItemResponse from(CalendarItem item) {
        return new CalendarItemResponse(
                item.type(), item.id(), item.title(), item.tag(),
                item.startDate(), item.endDate(), item.isDone());
    }
}
