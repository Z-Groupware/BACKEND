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

import com.module06.backend.capture.domain.model.VectorProvenance;

/*
 * meeting_tuple_vector(V5.10) 매핑이다. few-shot 예시의 **원본**이고 Qdrant 는 인덱스다.
 *
 * vector_synced 를 여기서 true 로 만들지 않는다. 이 코드가 하는 일은 "예약"까지이고, 실제
 * 임베딩과 Qdrant upsert 는 AI-08 이 한다 — MySQL 을 먼저 커밋해야 벡터는 있는데 원본이 없는
 * 상태가 생기지 않는다(V5.10 주석). 그래서 synced_at · qdrant_point_id · sync_attempts 는
 * 매핑하지 않는다: 이쪽이 쓸 값이 아니고, DB 기본값이 채운다.
 *
 * dept_id 도 매핑하지 않는다. few-shot 범위를 같은 팀으로 좁힐 때 쓰는 값인데 그 판단은
 * AI-09 의 것이고, 지금 채우면 "무엇을 기준으로 좁혔는지"를 두 곳이 정하게 된다.
 */
@Entity
@Table(name = "meeting_tuple_vector")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingTupleVectorJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 빠지면 정확도 문제가 아니라 타사 데이터 유출이다 — 다른 회사 발화가 프롬프트에 실린다. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* 이 예시가 쓰일 계층. LayerName 의 전송값을 담는다(review_log.layer 와 같은 규칙). */
    @Column(name = "layer", nullable = false, length = 8)
    private String layer;

    /*
     * 임베딩 대상 = **근거 발화 원문**이다. 확정 tuple 이 아니다 — 검색 시점에 손에 있는 것은
     * tuple 이 아니라 새 발화이므로, tuple 을 임베딩하면 쿼리와 키가 다른 공간에 놓인다.
     */
    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;

    /* 확정 tuple. 검색 결과로 그대로 실려 나간다. */
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    /*
     * 사람이 확인한 것만 예시로 쓴다. AUTO 를 예시로 쓰면 **모델이 자기 출력을 다시 학습하는
     * 루프**가 생긴다(V5.10 주석). 이 API 가 만드는 것은 사람의 판정이므로 항상 HUMAN_VERIFIED 다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", nullable = false)
    private VectorProvenance provenance;

    /* 이 예시를 만든 라벨. "어느 판정에서 나온 예시인가"를 되짚는다. */
    @Column(name = "review_log_id")
    private Long reviewLogId;

    /* Qdrant 반영 여부. false 면 재시도 워커 대상이다 — 예약 시점에는 항상 false 다. */
    @Column(name = "vector_synced", nullable = false)
    private boolean vectorSynced;

    public static MeetingTupleVectorJpaEntity queued(long companyId, long meetingId, String layer,
                                                     String inputText, String payload, Long reviewLogId) {
        MeetingTupleVectorJpaEntity entity = new MeetingTupleVectorJpaEntity();
        entity.companyId = companyId;
        entity.meetingId = meetingId;
        entity.layer = layer;
        entity.inputText = inputText;
        entity.payload = payload;
        entity.provenance = VectorProvenance.HUMAN_VERIFIED;
        entity.reviewLogId = reviewLogId;
        entity.vectorSynced = false;
        return entity;
    }
}
