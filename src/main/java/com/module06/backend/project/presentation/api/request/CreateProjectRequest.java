package com.module06.backend.project.presentation.api.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.module06.backend.project.domain.model.ProjectColorPalette;

/* comment.
    프로젝트 생성 요청 DTO. tag는 URL 식별자라 영문·숫자·-_ 만 허용(1차 검증), 중복 여부는
    service가 판단한다. 길이 제한은 DB 컬럼(name 150/tag 30) 기준 + Figma 실제 UI(tag 8자) 반영.
*/
public record CreateProjectRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 8) @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "태그는 영문·숫자·-_ 만 허용합니다.") String tag,
        String description,
        @NotBlank @Pattern(
                regexp = ProjectColorPalette.REGEXP,
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "색상은 지정된 팔레트 11색 중 하나여야 합니다."
        ) String color,
        @NotNull LocalDate dueDate,
        // Figma 생성폼엔 필수(*)로 표시되는데 @NotNull만으로는 빈 배열([])이 통과했다
        // — 이태연(review) 제보로 2026-08-07 확인 후 추가.
        @NotEmpty List<Long> teamIds
) {
}
