package com.module06.backend.action.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;

import lombok.Getter;

/* comment.
    액션 애그리거트 루트. TEAM/PERSONAL 두 종류를 한 테이블·한 모델로 표현하는 자기참조 구조다.
    TEAM은 담당자 없이 팀 단위로 존재하며 한 팀에 여러 개 동시에 있을 수 있다.
    PERSONAL은 담당자(assignee) 1명과 상위 TEAM 액션(parentActionId)을 가진다.
    사용자는 액션을 직접 만들지 않는다 — A도메인이 ActionDistributionPort로 생성하며,
    AI가 놓친 액션만 "+" 버튼으로 사람이 예외적으로 수동 추가한다(FR-AC-01).
    지정 부서·담당자·프로젝트는 다른 도메인 엔티티를 참조하지 않고 id 값만 가진다(0절 절대규칙 1항).
    reassignTo는 인수인계(ActionReassignPort) 시 PERSONAL 액션의 담당자만 교체한다 — TEAM이면 거부.

    연결된 클래스
    - ActionType               : TEAM/PERSONAL 구분 (FR-AC-06, FR-AC-02)
    - ActionStatus              : 상태 값(TODO/IN_PROGRESS/DONE)
    - ActionChecklistItem      : 이 액션에 딸린 체크리스트 항목(FR-AC-05)
    - ActionTypeShapePolicy    : TEAM↔PERSONAL 필드 제약 규칙
    - ActionRepository         : 저장소 계약
    - ActionJpaEntity          : 영속화 매핑 (infrastructure.persistence)
*/
@Getter
public class Action {

    private final Long id;
    private final Long companyId;
    private final Long projectId;
    private final Long parentActionId;
    private final Long sourceMeetingId;
    private final Long teamId;
    private Long assigneeMemberId;
    private final ActionType actionType;
    private final String title;
    private final String description;
    private final ActionStatus status;
    private final LocalDate dueDate;
    private final LocalDateTime confirmedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Action(
            Long id,
            Long companyId,
            Long projectId,
            Long parentActionId,
            Long sourceMeetingId,
            Long teamId,
            Long assigneeMemberId,
            ActionType actionType,
            String title,
            String description,
            ActionStatus status,
            LocalDate dueDate,
            LocalDateTime confirmedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.companyId = companyId;
        this.projectId = projectId;
        this.parentActionId = parentActionId;
        this.sourceMeetingId = sourceMeetingId;
        this.teamId = teamId;
        this.assigneeMemberId = assigneeMemberId;
        this.actionType = actionType;
        this.title = title;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.confirmedAt = confirmedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isPersonal() {
        return actionType == ActionType.PERSONAL;
    }

    public void reassignTo(Long toMemberId) {
        if (!isPersonal()) {
            throw new BusinessException(ActionErrorCode.CANNOT_REASSIGN_TEAM_ACTION);
        }
        this.assigneeMemberId = toMemberId;
    }
}
