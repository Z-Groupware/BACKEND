package com.module06.backend.project.application.usecase;

import com.module06.backend.project.application.command.UpdateProjectCommand;
import com.module06.backend.project.domain.model.Project;

/* comment.
    FR-PJ-03 — 프로젝트 수정 기능 계약. OWNER만 호출할 수 있다(조회는 열렸지만 수정은 아니다).
*/
public interface UpdateProjectUseCase {

    Project update(UpdateProjectCommand command);
}
