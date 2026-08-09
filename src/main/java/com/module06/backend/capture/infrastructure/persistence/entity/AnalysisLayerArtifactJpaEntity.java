package com.module06.backend.capture.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.capture.domain.model.LayerName;

/*
 * analysis_layer_artifact(V5.20) 매핑이다. 계층 재개(ANLZ-02)가 되살릴 중간 산출물을 담는다.
 *
 * layer 를 String 으로 들고 있는 이유는 {@link AnalysisLayerJpaEntity} 와 같다 — 값이
 * "L1.5" 라 enum 이름("L1_5")과 다르고, 변환은 {@link LayerName#wireValue()} 한 곳에서만 한다.
 *
 * payload 는 통째로 바꾼다. 부분 갱신을 두지 않는 이유 — 이 산출물은 한 계층 실행의 결과
 * 전체이고, 일부만 갈아끼우면 한 회의의 문맥이 두 실행에 걸치게 된다.
 */
@Entity
@Table(name = "analysis_layer_artifact")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisLayerArtifactJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "layer", nullable = false, length = 8)
    private String layer;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    private AnalysisLayerArtifactJpaEntity(Long meetingId, LayerName layer, String payload) {
        this.meetingId = meetingId;
        this.layer = layer.wireValue();
        this.payload = payload;
    }

    public static AnalysisLayerArtifactJpaEntity of(long meetingId, LayerName layer, String payload) {
        return new AnalysisLayerArtifactJpaEntity(meetingId, layer, payload);
    }

    public void replacePayload(String payload) {
        this.payload = payload;
    }
}
