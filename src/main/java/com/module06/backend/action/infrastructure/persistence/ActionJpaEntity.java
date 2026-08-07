package com.module06.backend.action.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.model.AssigneeSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    action 테이블 JPA 매핑. TEAM/PERSONAL 한 테이블 자기참조 구조를 그대로 반영한다.
    V2.6.1~4의 AI 분배 메타 컬럼(assignee_source·evidence_transcript_id·gate_signals·
    is_manual·review_status·due_date_defaulted)까지 매핑한다 — ddl-auto=validate라
    누락되면 기동은 되지만 분배 시 값이 조용히 DB 기본값으로 떨어진다.
    gate_signals(JSON)는 C가 내용을 해석하지 않고 그대로 보관하는 값이라 String으로 두고
    columnDefinition만 json으로 맞춘다(handover의 handover_insight.payload와 동일 패턴).
    pending_handover_ack(V2.6.5)는 acknowledge 엔드포인트 착수 시 추가 — 지금은 매핑하지
    않으므로 insert 시 DB 기본값(FALSE)이 들어가고, update 시에도 건드리지 않는다.
*/
@Entity
@Table(name = "action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "parent_action_id")
    private Long parentActionId;

    @Column(name = "source_meeting_id")
    private Long sourceMeetingId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "assignee_member_id")
    private Long assigneeMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // 2026-08-07 isDone 재설계 — status는 더 이상 원본이 아니라 isDone·startDate의 거울이다.
    // 컬럼 자체는 NOT NULL이라 여전히 값을 갖고 저장하지만, 읽을 때는 항상 Action.reconstitute가
    // isDone·startDate로부터 다시 계산해서 덮는다(이 필드값을 직접 신뢰하지 않는다).
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActionStatus status;

    @Column(name = "is_done", nullable = false)
    private boolean isDone;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "due_date_defaulted", nullable = false)
    private boolean dueDateDefaulted;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ActionReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_source")
    private AssigneeSource assigneeSource;

    @Column(name = "evidence_transcript_id")
    private Long evidenceTranscriptId;

    @Column(name = "gate_signals", columnDefinition = "json")
    private String gateSignals;

    @Column(name = "is_manual", nullable = false)
    private boolean isManual;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /* comment.
        분배 확정(RVW-05)으로 보드에 내보낸 시각(V5.17). NULL이면 아직 아무 데도 가 있지 않다 —
        화면의 "확정 전 검토 가능"이 그 뜻이다.

        confirmed_at과 다른 순간이다. 그쪽은 액션 하나를 사람이 확정·반려한 시각이고 반려하면
        지워지는데, 이 값은 회의의 검토를 끝내고 묶음을 내보낸 시각이며 한 번 나가면 되돌리지
        않는다. 담당자를 고쳐 확정했지만 아직 분배 버튼을 누르지 않은 액션이 그 차이다.

        도메인 모델(Action)이 아니라 엔티티에만 둔다. 다른 필드와 얽힌 전이 규칙이 없는
        기록용 시각이고, Action의 생성자·팩터리 시그니처를 넓히면 그 도메인을 쓰는 모든 경로가
        함께 흔들린다. "이미 나간 것을 다시 찍지 않는다"는 규칙은 아래 markDispatched가 갖는다.
    */
    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ActionJpaEntity(
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
            boolean isDone,
            LocalDate startDate,
            LocalDate dueDate,
            boolean dueDateDefaulted,
            ActionReviewStatus reviewStatus,
            AssigneeSource assigneeSource,
            Long evidenceTranscriptId,
            String gateSignals,
            boolean isManual,
            LocalDateTime confirmedAt
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
        this.isDone = isDone;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.dueDateDefaulted = dueDateDefaulted;
        this.reviewStatus = reviewStatus;
        this.assigneeSource = assigneeSource;
        this.evidenceTranscriptId = evidenceTranscriptId;
        this.gateSignals = gateSignals;
        this.isManual = isManual;
        this.confirmedAt = confirmedAt;
    }

    /* comment.
        분배 확정 시각을 찍는다(RVW-05).

        이미 찍힌 액션은 그대로 둔다 — 한 번 보드로 나간 액션이 두 번째 확정에서 시각만 갱신되면
        "언제 나갔나"의 답이 바뀐다. 회수 경로가 없으므로 나간 사실은 처음 것이 맞다.

        @return 이번 호출로 새로 찍혔으면 true
    */
    public boolean markDispatched(LocalDateTime dispatchedAt) {
        if (this.dispatchedAt != null) {
            return false;
        }
        this.dispatchedAt = dispatchedAt;
        return true;
    }
}
