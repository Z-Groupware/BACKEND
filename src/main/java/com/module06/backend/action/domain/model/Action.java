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
    지정 부서·담당자·프로젝트는 다른 도메인 엔티티를 참조하지 않고 id 값만 가진다(0절 절대규칙 1항).

    create()는 AI 분배(ActionDistributionPort)의 생성 경로다. 상태는 항상 TODO, 검토 상태는
    항상 PENDING으로 시작한다 — 자동확정(AUTO_CONFIRMED) 판정은 review(A)의 책임이라 C가
    분배 시점에 올려두지 않는다(결정로그 25번).
    dueDate는 컬럼이 NOT NULL이라 AI가 기한을 비워 보내면 프로젝트 마감일로 채워서 들어오며,
    그렇게 채운 경우 dueDateDefaulted=true로 표시한다 — review의 WRONG_DUE 반려 판정이
    "AI가 정한 기한"과 "기본값으로 채운 기한"을 섞어 보지 않게 하기 위함이다(V2.6.4).
    gateSignals는 자동확정 4조건의 판정 결과 JSON을 C가 해석하지 않고 그대로 보관하는 값이라
    String으로 둔다(V2.6.1).
    상태변경·리뷰확정·체크리스트는 각 유스케이스 착수 시 추가한다.
    pendingHandoverAck(V2.6.5)는 acknowledge 엔드포인트 착수 시 매핑 — 아직 필드로 두지 않는다.

    연결된 클래스
    - ActionType            : TEAM/PERSONAL 구분 (FR-AC-06, FR-AC-02)
    - ActionStatus          : 상태 값(TODO/IN_PROGRESS/DONE)
    - ActionReviewStatus    : AI 분배 검토 상태 (FR-AC-04)
    - AssigneeSource        : AI가 담당자를 특정한 근거
    - ActionChecklistItem   : 이 액션에 딸린 체크리스트 항목(FR-AC-05)
    - ActionTypeShapePolicy : TEAM↔PERSONAL 필드 제약 규칙
    - ActionRepository      : 저장소 계약
    - ActionJpaEntity       : 영속화 매핑 (infrastructure.persistence)
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
    private final boolean dueDateDefaulted;
    private final ActionReviewStatus reviewStatus;
    private final AssigneeSource assigneeSource;
    private final Long evidenceTranscriptId;
    private final String gateSignals;
    private final boolean isManual;
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
            boolean dueDateDefaulted,
            ActionReviewStatus reviewStatus,
            AssigneeSource assigneeSource,
            Long evidenceTranscriptId,
            String gateSignals,
            boolean isManual,
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
        this.dueDateDefaulted = dueDateDefaulted;
        this.reviewStatus = reviewStatus;
        this.assigneeSource = assigneeSource;
        this.evidenceTranscriptId = evidenceTranscriptId;
        this.gateSignals = gateSignals;
        this.isManual = isManual;
        this.confirmedAt = confirmedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 사람이 "+"로 직접 추가(FR-AC-01 예외 경로). AI를 거치지 않으므로 검토 자체가 필요 없다 —
    // reviewStatus를 곧장 HUMAN_CONFIRMED로, confirmedAt을 생성 시각으로 채운다.
    // sourceMeetingId·parentActionId가 없어 assigneeSource·evidenceTranscriptId·gateSignals도
    // 유도할 근거가 없으므로 null, dueDate는 사용자가 직접 입력해 defaulted 여지가 없다.
    public static Action createManual(
            Long companyId,
            Long projectId,
            Long teamId,
            Long assigneeMemberId,
            ActionType actionType,
            String title,
            String description,
            LocalDate dueDate
    ) {
        return new Action(
                null, companyId, projectId, null, null, teamId, assigneeMemberId,
                actionType, title, description, ActionStatus.TODO, dueDate, false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, true,
                LocalDateTime.now(), null, null
        );
    }

    // AI 분배로 신규 생성(FR-AC-01 정상 경로). id·타임스탬프는 저장소가 채우고,
    // status는 TODO·reviewStatus는 PENDING·confirmedAt은 null로 고정한다.
    public static Action create(
            Long companyId,
            Long projectId,
            Long parentActionId,
            Long sourceMeetingId,
            Long teamId,
            Long assigneeMemberId,
            ActionType actionType,
            String title,
            String description,
            LocalDate dueDate,
            boolean dueDateDefaulted,
            AssigneeSource assigneeSource,
            Long evidenceTranscriptId,
            String gateSignals,
            boolean isManual
    ) {
        return new Action(
                null, companyId, projectId, parentActionId, sourceMeetingId, teamId, assigneeMemberId,
                actionType, title, description, ActionStatus.TODO, dueDate, dueDateDefaulted,
                ActionReviewStatus.PENDING, assigneeSource, evidenceTranscriptId, gateSignals, isManual,
                null, null, null
        );
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
            boolean dueDateDefaulted,
            ActionReviewStatus reviewStatus,
            AssigneeSource assigneeSource,
            Long evidenceTranscriptId,
            String gateSignals,
            boolean isManual,
            LocalDateTime confirmedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new Action(
                id, companyId, projectId, parentActionId, sourceMeetingId, teamId, assigneeMemberId,
                actionType, title, description, status, dueDate, dueDateDefaulted,
                reviewStatus, assigneeSource, evidenceTranscriptId, gateSignals, isManual,
                confirmedAt, createdAt, updatedAt
        );
    }

    public boolean isPersonal() {
        return this.actionType == ActionType.PERSONAL;
    }

    // 담당자 교체 — 인수인계(E) 재분배 전용. TEAM 액션은 담당자 개념이 없어 대상 아님.
    public void reassignTo(Long newAssigneeMemberId) {
        if (newAssigneeMemberId == null) {
            throw new IllegalArgumentException("newAssigneeMemberId는 null일 수 없습니다.");
        }
        if (!isPersonal()) {
            throw new IllegalStateException("TEAM 액션은 담당자를 교체할 수 없습니다.");
        }
        this.assigneeMemberId = newAssigneeMemberId;
    }
}
