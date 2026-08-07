package com.module06.backend.capture.infrastructure.persistence.entity;

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

import com.module06.backend.capture.domain.model.RejectReason;
import com.module06.backend.capture.domain.model.ReviewDecision;
import com.module06.backend.capture.domain.model.ReviewTargetType;

/*
 * review_log(V5.9) 매핑이다. **append-only 라 수정 메서드가 없다.**
 *
 * 같은 액션을 두 번 판정하면 행이 둘 남는 것이 맞다 — 사람이 마음을 바꾼 것도 라벨이고,
 * 덮어쓰면 "처음에 무엇이 틀렸다고 봤는지"가 사라진다. 그래서 이 테이블에는 updated_at 도 없다.
 *
 * JSON 컬럼 셋은 String 으로 두고 columnDefinition 만 json 으로 맞춘다 — 내용을 이 계층이
 * 해석하지 않고 그대로 보관하는 값이다(action.gate_signals · handover_insight.payload 와 같은
 * 패턴). 여기서 객체로 매핑하면 라벨 구조가 바뀔 때마다 마이그레이션이 필요해지는데,
 * input_context 는 앞으로 계속 늘어난다.
 *
 * created_at 은 매핑하지 않는다. DB 기본값(CURRENT_TIMESTAMP)이 채우고 이 코드가 읽을 일이
 * 없다 — 라벨을 꺼내 쓰는 것은 AI-08·09 이고 그쪽은 이 엔티티를 지나지 않는다.
 */
@Entity
@Table(name = "review_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* DB ENUM + @Enumerated(STRING). meeting_assignment_tuple.assignee_source 와 같은 짝이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReviewTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /*
     * 이 라벨이 교정하는 계층. LayerName 의 전송값("L1.5")을 문자열로 담는다 —
     * enum 이름(L1_5)을 저장하면 같은 계층이 파이썬 쪽 "L1.5" 와 갈린다(LayerName 주석).
     */
    @Column(name = "layer", nullable = false, length = 8)
    private String layer;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false)
    private ReviewDecision decision;

    /* MODIFY·REJECT 에는 필수다. DB CHECK(CK_REVIEW_LOG_REASON)가 조합을 강제한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason")
    private RejectReason rejectReason;

    @Column(name = "input_context", nullable = false, columnDefinition = "json")
    private String inputContext;

    @Column(name = "llm_output", nullable = false, columnDefinition = "json")
    private String llmOutput;

    /* CONFIRM 이면 NULL 이다 — llm_output 과 같다는 뜻이다. */
    @Column(name = "human_value", columnDefinition = "json")
    private String humanValue;

    @Column(name = "confirmed_by", nullable = false)
    private Long confirmedBy;

    @Column(name = "model_name", length = 60)
    private String modelName;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    /* RVW-03 직접 추가 건. few-shot 풀에서 제외되고 gold set 으로는 유효하다. */
    @Column(name = "is_manual", nullable = false)
    private boolean isManual;

    public static ReviewLogJpaEntity of(long companyId, long meetingId, ReviewTargetType targetType,
                                        long targetId, String layer, ReviewDecision decision,
                                        RejectReason rejectReason, String inputContext, String llmOutput,
                                        String humanValue, long confirmedBy, String modelName,
                                        String promptVersion, boolean isManual) {
        ReviewLogJpaEntity entity = new ReviewLogJpaEntity();
        entity.companyId = companyId;
        entity.meetingId = meetingId;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.layer = layer;
        entity.decision = decision;
        entity.rejectReason = rejectReason;
        entity.inputContext = inputContext;
        entity.llmOutput = llmOutput;
        entity.humanValue = humanValue;
        entity.confirmedBy = confirmedBy;
        entity.modelName = modelName;
        entity.promptVersion = promptVersion;
        entity.isManual = isManual;
        return entity;
    }
}
