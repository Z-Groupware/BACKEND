package com.module06.backend.calendar.presentation.api.response;

import java.time.LocalDate;

import com.module06.backend.calendar.application.usecase.CalendarItem;
import com.module06.backend.calendar.application.usecase.CalendarItemType;

public record CalendarItemResponse(
        CalendarItemType type,
        String title,
        String tag,
        LocalDate startDate,
        LocalDate endDate
) {

    public static CalendarItemResponse from(CalendarItem item) {
        return new CalendarItemResponse(
                item.type(), item.title(), item.tag(), item.startDate(), item.endDate());
    }
}
