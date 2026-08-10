package com.module06.backend.project.application.command;

import java.time.LocalDate;
import java.util.List;

/* comment.
    프로젝트 수정 입력값. tag는 담지 않는다(FR-PJ-04 불변). status도 안 담는다 —
    상태 변경은 BulkUpdateProjectStatusUseCase(보드 저장) 몫으로 분리돼있다.
*/
public record UpdateProjectCommand(
        Long projectId,
        Long requesterId,
        String name,
        String description,
        String color,
        LocalDate startDate,
        LocalDate dueDate,
        List<Long> teamIds
) {
}
