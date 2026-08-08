package com.module06.backend.calendar.presentation.api.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/* comment.
    Todo 추가 요청 DTO. Figma 모달 필드 그대로 — title·date 둘뿐(기간·시간 없음).
*/
public record CreateTodoRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate date
) {
}
