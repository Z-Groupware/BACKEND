package com.module06.backend.project.application.usecase;

import com.module06.backend.project.application.command.CreateProjectCommand;
import com.module06.backend.project.domain.model.Project;

/* comment.
    FR-PJ-01 — 프로젝트 생성 기능 계약. OWNER만 호출할 수 있다(권한 판단은 구현체 몫).
*/
public interface CreateProjectUseCase {

    Project create(CreateProjectCommand command);
}
