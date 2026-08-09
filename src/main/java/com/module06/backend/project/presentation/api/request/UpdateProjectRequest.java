package com.module06.backend.project.presentation.api.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/* comment.
    프로젝트 수정 요청 DTO. 이름·기획(description)·색상·마감일·지정 부서를 담는다.
    tag·status 필드는 두지 않는다 — tag는 생성 후 불변(FR-PJ-04), status는
    BulkUpdateProjectStatusUseCase(보드 저장) 몫으로 분리돼있다(UpdateProjectCommand와 동일 이유).

    연결된 클래스
    - ProjectController    : 이 DTO를 받는 진입점
    - UpdateProjectCommand : 이 DTO가 변환되는 application 명령
*/
public record UpdateProjectRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다.") String color,
        @NotNull LocalDate dueDate,
        @NotNull List<Long> teamIds
) {
}
