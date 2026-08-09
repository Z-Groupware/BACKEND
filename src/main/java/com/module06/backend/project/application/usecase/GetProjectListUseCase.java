package com.module06.backend.project.application.usecase;

import java.util.List;

import com.module06.backend.project.domain.model.Project;

/* comment.
    FR-PJ-02 — 프로젝트 목록 조회 기능 계약. 전 구성원(MEMBER 이상) 공개다.
    actionCount·completedActionCount는 2026-08-05엔 action 도메인 미비로 스텁(0)이었으나,
    action BC가 다 갖춰진 지금은 배치 집계로 채운다(2026-08-09) — ProjectDetailResult와
    같은 이유로 Project 단독이 아니라 함께 반환한다.
*/
public interface GetProjectListUseCase {

    List<ProjectListItem> list(Long companyId);

    record ProjectListItem(Project project, int actionCount, int completedActionCount) {
    }
}
