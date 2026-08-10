package com.module06.backend.project.application.usecase;

import java.util.List;

import com.module06.backend.project.domain.model.Project;

/* comment.
    FR-PJ-02 — 프로젝트 목록 조회 기능 계약. 전 구성원(MEMBER 이상) 공개다.
    actionCount·completedActionCount·meetingCount는 2026-08-05엔 미구현이라 스텁(0)이었으나,
    action BC·meeting(D) 계약이 다 갖춰진 지금은 배치 집계로 채운다(2026-08-09) —
    ProjectDetailResult와 같은 이유로 Project 단독이 아니라 함께 반환한다.
    teamNames는 목록 행의 부서 칩 표시용 — team(B) 참조 테이블에서 배치 조회해 채운다(2026-08-09, 윤종호 확인).

    2026-08-10 페이지네이션 도입(이홍근 요청) — page는 0부터 시작. totalElements는 페이지와
    무관하게 전체 건수라 ProjectListResult로 함께 반환한다.
*/
public interface GetProjectListUseCase {

    ProjectListResult list(Long companyId, int page, int size);

    record ProjectListItem(
            Project project,
            int actionCount,
            int completedActionCount,
            int meetingCount,
            List<String> teamNames
    ) {
    }

    record ProjectListResult(List<ProjectListItem> items, long totalElements) {
    }
}
