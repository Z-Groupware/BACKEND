package com.module06.backend.project.presentation.api.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/* comment.
    프로젝트 생성 요청 DTO. tag는 URL 식별자라 영문·숫자·-_ 만 허용(1차 검증), 중복 여부는
    service가 판단한다.
*/
public record CreateProjectRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "태그는 영문·숫자·-_ 만 허용합니다.") String tag,
        String description,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다.") String color,
        @NotNull LocalDate dueDate,
        @NotNull List<Long> teamIds
) {
}
