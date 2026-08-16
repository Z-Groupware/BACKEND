package com.module06.backend.action.application.port;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;

/* comment.
    action이 선언하고, project(C 내부, 타임라인) 도메인이 호출하는 조회 포트.
    project는 action 엔티티·Repository를 직접 참조하지 않고 이 계약으로만 팀 액션 정보를
    조회한다(0절 절대규칙 1항 — 도메인 간 엔티티 직접 참조 금지, ProjectQueryPort와 동일 패턴).
*/
public interface ActionQueryPort {

    // FR-PJ-07 프로젝트 타임라인 탭 표시용 — 해당 프로젝트에 속한 TEAM 액션 전체(팀명 포함).
    List<TeamActionSummary> findTeamActionsByProjectId(Long projectId);

    // 프로젝트 목록 진행률(actionCount/completedActionCount) 표시용 배치 조회(2026-08-09).
    // 목록 화면에서 프로젝트마다 따로 부르면 N+1이라, projectId 목록을 한 번에 받아 집계까지 끝낸다.
    // PERSONAL 액션만 센다(팀 액션 제외). companyId는 다른 회사 행이 섞이지 않게 조회 자체에서 막는다.
    List<ProjectActionCount> countActionsByProjectIds(Long companyId, List<Long> projectIds);

    record TeamActionSummary(Long actionId, String title, Long teamId, String teamName, ActionStatus status, LocalDate dueDate) {

        /* 지연 판정식은 action 도메인 것이다. project가 자기 식을 따로 쓰면 같은 액션이 화면마다
           다른 배지를 단다 — 그래서 계산을 여기서 대신 해 준다(호출부는 식을 몰라도 된다). */
        public boolean isDelayed(LocalDate today) {
            return Action.isDelayed(status, dueDate, today);
        }
    }

    record ProjectActionCount(Long projectId, int totalCount, int completedCount) {
    }
}
