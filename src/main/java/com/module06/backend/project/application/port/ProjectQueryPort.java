package com.module06.backend.project.application.port;

import java.util.List;

/* comment.
    project(C)가 선언하고, meeting(D, 모성진) 도메인이 호출하는 인바운드 포트.
    D는 project 엔티티·Repository를 직접 참조하지 않고 이 계약으로만 프로젝트 정보를 조회한다.
*/
public interface ProjectQueryPort {

    // MEET-01 회의 개설 시 사용 — 다른 회사 소속이거나 soft-delete된 프로젝트는 false.
    boolean existsActiveProject(Long companyId, Long projectId);

    // MEET-03 예정 회의 목록 표시용 배치 조회 — soft-delete된 프로젝트도 포함(과거 회의가 참조를 유지해야 함).
    List<ProjectSummary> findProjects(Long companyId, List<Long> projectIds);

    record ProjectSummary(Long projectId, String tag, String name, String color) {
    }
}
