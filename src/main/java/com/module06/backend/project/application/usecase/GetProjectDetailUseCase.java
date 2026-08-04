package com.module06.backend.project.application.usecase;

import java.util.List;

import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectAttachment;

/* comment.
    FR-PJ-02 — 프로젝트 상세 조회(기획 탭) 기능 계약. 전 구성원 공개, 기획(description) 포함.
    첨부파일 목록이 응답에 인라인으로 실린다.
*/
public interface GetProjectDetailUseCase {

    ProjectDetailResult getDetail(Long projectId);

    record ProjectDetailResult(Project project, List<ProjectAttachment> attachments) {
    }
}
