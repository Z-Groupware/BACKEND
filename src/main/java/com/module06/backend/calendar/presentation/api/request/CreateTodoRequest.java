package com.module06.backend.calendar.presentation.api.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/* comment.
    Todo 추가 요청 DTO. endDate는 선택값(nullable) — 미지정 시 서비스 계층에서 date와
    동일값으로 채워 단일 날짜 Todo로 동작한다(기간 지원 추가분, #458). endDate < date
    검증은 여기(구조 검증)가 아니라 서비스 계층에서 BusinessException(CAL-002)으로 한다.
*/
public record CreateTodoRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate date,
        LocalDate endDate
) {
}
