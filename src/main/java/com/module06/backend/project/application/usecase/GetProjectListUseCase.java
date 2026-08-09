package com.module06.backend.project.application.usecase;

import java.util.List;

import com.module06.backend.project.domain.model.Project;

/* comment.
    FR-PJ-02 — 프로젝트 목록 조회 기능 계약. 전 구성원(MEMBER 이상) 공개다.
*/
public interface GetProjectListUseCase {

    List<Project> list(Long companyId);
}
