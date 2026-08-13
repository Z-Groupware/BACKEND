package com.module06.backend.calendar.application.usecase;

import java.time.LocalDate;

/* comment.
    GET /api/calendar 통합 응답 항목. PROJECT/ACTION/TODO 세 도메인의 서로 다른 shape을
    하나로 맞춘다 — 단일 날짜 항목(ACTION·TODO)은 startDate와 endDate가 같다.

    id·isDone은 TODO 전용 필드다(PROJECT·ACTION은 null) — FE가 캘린더 화면에서 Todo
    체크박스를 렌더링하고 완료 토글(PATCH /api/todos/{id}/complete)을 호출하려면
    항목의 실제 id가 필요한데, 기존엔 이 통합조회 응답에서 빠져 있었다(#456).
*/
public record CalendarItem(
        CalendarItemType type,
        Long id,
        String title,
        String tag,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isDone
) {
}
