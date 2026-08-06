package com.module06.backend.project.application.usecase;

import java.util.List;

import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectAttachment;

/* comment.
    FR-PJ-02 — 프로젝트 상세 조회(기획 탭) 기능 계약. 전 구성원 공개, 기획(description) 포함.
    첨부파일 목록이 응답에 인라인으로 실린다. companyId는 소속 회사 프로젝트만 조회 가능하도록
    막는 2차 방어(다른 회사 프로젝트를 id로 직접 조회하는 것 차단).
*/
public interface GetProjectDetailUseCase {

    ProjectDetailResult getDetail(Long companyId, Long projectId);

    record ProjectDetailResult(Project project, List<ProjectAttachment> attachments) {
    }
}
