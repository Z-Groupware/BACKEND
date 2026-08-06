package com.module06.backend.capture.infrastructure.persistence.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.VerifyVerdict;

/*
 * meeting_assignment_tuple(V5.12) 매핑이다.
 *
 * actionId 에 세터를 두지 않는다. 분배(action 생성)는 C 도메인의 상태 전이를 거쳐야 하고
 * 그 경로가 아직 정해지지 않았다 — 지금 세터를 열어 두면 그 경로를 지나지 않고 값을 넣는
 * 코드가 먼저 생긴다. 필드만 매핑해 두는 것은 컬럼이 있는데 엔티티가 모르면
 * ddl-auto=validate 가 부팅을 막기 때문이다.
 *
 * assigneeCandidateMemberId 는 Python 의 assigneeCandidatePersonId 와 같은 값이다.
 * 이 DB 에 person 이 없고 참석자 명단을 member 에서 만들므로 personId = member.id 다.
 */
@Entity
@Table(name = "meeting_assignment_tuple")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingAssignmentTupleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* 이 tuple 을 뽑은 CONFIRMED 항목. 삭제되면 DB 가 이 행을 함께 지운다(ON DELETE CASCADE). */
    @Column(name = "meeting_decision_id")
    private Long meetingDecisionId;

    @Column(name = "topic_seq", nullable = false)
    private Integer topicSeq;

    @Column(name = "topic", nullable = false, length = 300)
    private String topic;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    /* NULL = 명단 밖(unknown_person) 또는 담당자 미정. 임의로 채우지 않는다. */
    @Column(name = "assignee_candidate_member_id")
    private Long assigneeCandidateMemberId;

    /*
     * DB 는 ENUM 이고 여기는 @Enumerated(STRING) 이다. length 를 적지 않는 것은
     * meeting_decision.item_type 과 같은 짝이다 — ENUM 컬럼에 길이를 명시한 매핑이 이 저장소에
     * 아직 없어서, 검증되지 않은 조합을 새로 만들지 않는다(ddl-auto=validate 는 운영에서만 돈다).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_source")
    private AssigneeSource assigneeSource;

    /* NULL 이 정상이다 — 기한을 말하지 않았거나 기준일을 몰라 계산하지 않은 경우다. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "evidence_transcript_id")
    private Long evidenceTranscriptId;

    @Column(name = "model_name", length = 60)
    private String modelName;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    /* 분배 결과. 이 슬라이스에서는 항상 NULL — 분배 경로가 아직 없다. */
    @Column(name = "action_id")
    private Long actionId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /*
     * ── L5 관점 다변화 검증 결과 (V5.13) ────────────────────────────────────────
     *
     * 전부 nullable 이고, NULL 이 "L5 가 이 행을 아직 보지 않았다"는 뜻이다. 저장 시점
     * (L4)에는 항상 NULL 이고 {@link #applyVerification} 만 채운다 — of() 로 채우면
     * 검증받지 않은 tuple 이 검증된 것으로 저장되는 경로가 생긴다(gate_status 와 같은 짝).
     *
     * verifyAgree 를 Boolean(래퍼)으로 둔다. boolean 이면 미검증이 false 와 같아지고,
     * "검증에서 걸린 것"과 "검증을 안 한 것"이 한 값으로 뭉친다.
     */
    @Column(name = "verify_agree")
    private Boolean verifyAgree;

    @Column(name = "verify_disagreement_fields", length = 200)
    private String verifyDisagreementFields;

    /* assignee_source 와 같은 매핑이다 — DB ENUM + @Enumerated(STRING), length 없음. */
    @Enumerated(EnumType.STRING)
    @Column(name = "verify_verdict")
    private VerifyVerdict verifyVerdict;

    @Column(name = "verify_reason", length = 500)
    private String verifyReason;

    @Column(name = "verify_model_name", length = 60)
    private String verifyModelName;

    @Column(name = "verify_prompt_version", length = 20)
    private String verifyPromptVersion;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    public static MeetingAssignmentTupleJpaEntity of(long companyId, long meetingId, Long meetingDecisionId,
                                                     int topicSeq, String topic, String title,
                                                     Long assigneeCandidateMemberId, AssigneeSource assigneeSource,
                                                     LocalDate dueDate, Long evidenceTranscriptId,
                                                     String modelName, String promptVersion, int sortOrder) {
        MeetingAssignmentTupleJpaEntity entity = new MeetingAssignmentTupleJpaEntity();
        entity.companyId = companyId;
        entity.meetingId = meetingId;
        entity.meetingDecisionId = meetingDecisionId;
        entity.topicSeq = topicSeq;
        entity.topic = clip(topic, 300);
        entity.title = clip(title, 300);
        entity.assigneeCandidateMemberId = assigneeCandidateMemberId;
        entity.assigneeSource = assigneeSource;
        entity.dueDate = dueDate;
        entity.evidenceTranscriptId = evidenceTranscriptId;
        entity.modelName = clip(modelName, 60);
        entity.promptVersion = clip(promptVersion, 20);
        entity.sortOrder = sortOrder;
        return entity;
    }

    /*
     * L5 판정을 적는다. **덮어쓰기다** — 재검증은 이전 판정을 남길 이유가 없다.
     *
     * verifiedAt 을 함께 찍는 이유: agree=false 인 행이 "방금 검증에서 걸린 것"인지 "예전
     * 판정이 그대로 남은 것"인지 구분해야 한다. tuple 은 재실행마다 통째로 갈리지만(replace),
     * L4 만 재실행하는 경로가 ANLZ-02 로 붙으면 두 시점이 갈린다.
     *
     * disagreementFields 는 쉼표로 이어 붙인다. 이 조립을 어댑터가 아니라 엔티티가 하는
     * 이유는 컬럼 길이(200)를 아는 곳이 여기이기 때문이다.
     */
    public void applyVerification(boolean agree, List<String> disagreementFields, VerifyVerdict verdict,
                                  String reason, String modelName, String promptVersion) {
        this.verifyAgree = agree;
        this.verifyDisagreementFields = clip(joinFields(disagreementFields), 200);
        this.verifyVerdict = verdict;
        this.verifyReason = clip(reason, 500);
        this.verifyModelName = clip(modelName, 60);
        this.verifyPromptVersion = clip(promptVersion, 20);
        this.verifiedAt = LocalDateTime.now();
    }

    /*
     * 빈 목록은 NULL 로 둔다. 빈 문자열로 두면 "갈린 필드가 없다"와 구분되지 않는데,
     * 정확히는 같은 뜻이므로 값을 하나로 모은다 — 두 표현이 섞이면 조회 조건이 둘로 갈린다.
     */
    private static String joinFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        return String.join(",", fields);
    }

    /*
     * 컬럼 길이를 넘기면 저장이 통째로 터진다. 계층이 이미 자르고 있지만, 그쪽 상한이
     * 바뀌었을 때 회의 하나의 분석 전체가 실패하는 것보다 여기서 자르는 편이 낫다
     * (MeetingDecisionJpaEntity 와 같은 이유).
     */
    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
