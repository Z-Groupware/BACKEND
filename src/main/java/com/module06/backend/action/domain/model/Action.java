package com.module06.backend.action.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;

/* comment.
    액션 애그리거트 루트. TEAM/PERSONAL 두 종류를 한 테이블·한 모델로 표현하는 자기참조 구조다.
    TEAM은 담당자 없이 팀 단위로 존재하며 한 팀에 여러 개 동시에 있을 수 있다.
    PERSONAL은 담당자(assignee) 1명과 상위 TEAM 액션(parentActionId)을 가진다.
    사용자는 액션을 직접 만들지 않는다 — A도메인이 ActionDistributionPort로 생성하며,
    AI가 놓친 액션만 "+" 버튼으로 사람이 예외적으로 수동 추가한다(FR-AC-01).
    needsReview는 담당자의 최초 PATCH로 확정되며, 이후 재요청은 멱등하게 무시한다(FR-AC-04).
    지정 부서·담당자·프로젝트는 다른 도메인 엔티티를 참조하지 않고 id 값만 가진다(0절 절대규칙 1항).

    이번 슬라이스(ActionReassignPort 배선)엔 reconstitute()·reassignTo()만 필요해 그것만
    구현했다 — create()·상태변경·리뷰확정 등은 각 유스케이스 착수 시 추가.

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
    private final boolean needsReview;
    private final LocalDateTime confirmedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Action(
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
            boolean needsReview,
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
        this.needsReview = needsReview;
        this.confirmedAt = confirmedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 저장소가 조회 결과를 이 모델로 복원할 때 사용.
    public static Action reconstitute(
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
            boolean needsReview,
            LocalDateTime confirmedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new Action(
                id, companyId, projectId, parentActionId, sourceMeetingId, teamId, assigneeMemberId,
                actionType, title, description, status, dueDate, needsReview, confirmedAt, createdAt, updatedAt
        );
    }

    public boolean isPersonal() {
        return this.actionType == ActionType.PERSONAL;
    }

    // 담당자 교체 — 인수인계(E) 재분배 전용. TEAM 액션은 담당자 개념이 없어 대상 아님.
    public void reassignTo(Long newAssigneeMemberId) {
        if (!isPersonal()) {
            throw new IllegalStateException("TEAM 액션은 담당자를 교체할 수 없습니다.");
        }
        this.assigneeMemberId = newAssigneeMemberId;
    }
}
