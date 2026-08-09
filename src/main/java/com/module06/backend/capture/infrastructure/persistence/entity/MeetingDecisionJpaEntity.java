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

import com.module06.backend.capture.domain.model.GateStatus;
import com.module06.backend.capture.domain.model.ItemType;

/*
 * meeting_decision(V5.8) 매핑이다.
 *
 * ⚠ **복합 FK 가 걸려 있다** — (meeting_summary_id, meeting_id) → meeting_summary(id, meeting_id).
 * 그래서 meeting_id 를 반정규화해 들고 있으면서도 아무 값이나 넣을 수 없다. 요약을 먼저
 * 저장해 id 를 받은 다음 그 id 와 **같은 회의의** meeting_id 를 함께 넣어야 한다.
 * 어긋나면 저장이 FK 로 막힌다 — 그게 이 제약의 목적이다(다른 회의의 결정이 섞이는 것 차단).
 *
 * gate_status 는 L3.5 게이트만 채운다({@link #applyGate}). 저장 시점에는 항상 NULL 이고,
 * 그 NULL 이 "아직 판정되지 않았다"는 뜻이다 — 임의로 CONFIRMED 를 넣으면 게이트를 지나지
 * 않은 항목이 L4 로 흘러가고, 아직 합의도 안 된 논의가 담당자에게 배정된다.
 */
@Entity
@Table(name = "meeting_decision")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingDecisionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_summary_id", nullable = false)
    private Long meetingSummaryId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* L2 가 나눈 주제 순번이다. 여기서 새로 매기지 않는다. */
    @Column(name = "topic_seq", nullable = false)
    private Integer topicSeq;

    @Column(name = "topic", nullable = false, length = 300)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /* L3 가 이렇게 분류한 근거. 오분류 조사의 출발점이다(V5.8 주석). */
    @Column(name = "reason", length = 1000)
    private String reason;

    /* transcript_chunk.id. 근거 없는 항목은 애초에 계층이 반환하지 않는다. */
    @Column(name = "evidence_transcript_id")
    private Long evidenceTranscriptId;

    /* L3.5 판정. NULL 은 미판정이며 DISCUSSED 와 다르다 — L4 로 넘어가지 않는 건 같지만 이유가 다르다. */
    @Column(name = "gate_status", length = 20)
    private String gateStatus;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public static MeetingDecisionJpaEntity of(long summaryId, long meetingId, int topicSeq, String topic,
                                              ItemType itemType, String content, String reason,
                                              Long evidenceTranscriptId, int sortOrder) {
        MeetingDecisionJpaEntity entity = new MeetingDecisionJpaEntity();
        entity.meetingSummaryId = summaryId;
        entity.meetingId = meetingId;
        entity.topicSeq = topicSeq;
        entity.topic = clip(topic, 300);
        entity.itemType = itemType;
        entity.content = content;
        entity.reason = clip(reason, 1000);
        entity.evidenceTranscriptId = evidenceTranscriptId;
        entity.sortOrder = sortOrder;
        return entity;
    }

    /*
     * L3.5 판정을 기록한다. 이 값만 갱신 대상이다 — 게이트는 항목의 내용을 고치지 않는다.
     *
     * enum 을 받아 name() 을 넣는다. 문자열을 그대로 받으면 계층 응답의 알 수 없는 값이
     * 그대로 컬럼에 들어가고, gate_status 는 DB ENUM 이라 저장 단계에서 회의 전체가 실패한다.
     */
    public void applyGate(GateStatus status) {
        if (status == null) {
            return;
        }
        this.gateStatus = status.name();
    }

    /*
     * 컬럼 길이를 넘기면 저장이 통째로 터진다. 계층이 이미 자르고 있지만, 그쪽 상한이
     * 바뀌었을 때 회의 하나의 분석 전체가 실패하는 것보다 여기서 자르는 편이 낫다.
     */
    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
