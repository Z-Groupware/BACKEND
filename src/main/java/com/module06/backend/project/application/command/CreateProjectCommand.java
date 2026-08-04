package com.module06.backend.project.application.command;

import java.time.LocalDate;
import java.util.List;

/* comment.
    프로젝트 생성 입력값. companyId·createdBy는 임시로 X-Company-Id/X-Member-Id 헤더에서
    온다(JWT 미도입, 2026-08-05 결정) — Controller가 이 값을 채워 넘긴다.
*/
public record CreateProjectCommand(
        Long companyId,
        Long createdBy,
        String tag,
        String name,
        String description,
        String color,
        LocalDate dueDate,
        List<Long> teamIds
) {
}
