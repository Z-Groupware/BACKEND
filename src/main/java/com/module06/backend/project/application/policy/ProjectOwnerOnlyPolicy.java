package com.module06.backend.project.application.policy;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.exception.ProjectErrorCode;

/* comment.
    FR-PJ-01,03 — 프로젝트 생성·수정은 OWNER만 가능하다는 권한 규칙(2차 방어, 1차는 @PreAuthorize).
*/
public class ProjectOwnerOnlyPolicy {

    public void check(Project project, Long memberId) {
        if (!project.isOwnedBy(memberId)) {
            throw new BusinessException(ProjectErrorCode.NOT_PROJECT_OWNER);
        }
    }
}
