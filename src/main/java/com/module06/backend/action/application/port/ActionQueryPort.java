package com.module06.backend.action.application.port;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.action.domain.model.ActionStatus;

/* comment.
    action이 선언하고, project(C 내부, 타임라인) 도메인이 호출하는 조회 포트.
    project는 action 엔티티·Repository를 직접 참조하지 않고 이 계약으로만 팀 액션 정보를
    조회한다(0절 절대규칙 1항 — 도메인 간 엔티티 직접 참조 금지, ProjectQueryPort와 동일 패턴).
*/
public interface ActionQueryPort {

    // FR-PJ-07 프로젝트 타임라인 탭 표시용 — 해당 프로젝트에 속한 TEAM 액션 전체(팀명 포함).
    List<TeamActionSummary> findTeamActionsByProjectId(Long projectId);

    record TeamActionSummary(Long actionId, String title, Long teamId, String teamName, ActionStatus status, LocalDate dueDate) {
    }
}
