package com.module06.backend.calendar.application.usecase;

import java.time.YearMonth;
import java.util.List;

public interface GetCalendarUseCase {

    List<CalendarItem> getCalendar(Long companyId, Long memberId, String authority, YearMonth month);
}
