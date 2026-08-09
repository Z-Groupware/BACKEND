package com.module06.backend.cap.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    이태연(capture) 소유 analysis_layer 테이블을 읽기 전용으로 조회하기 위한 참조 엔티티. 녹음 삭제(CAP-15)의
    "분석이 다 끝났는가" 판정에만 status를 본다.

    ⚠️ Cap 접두어 필수: capture 도메인에 이미 AnalysisLayerJpaEntity(같은 테이블 매핑)가 있어, 단순명이
    겹치면 Duplicate entity mapping으로 컨텍스트가 죽는다. status는 capture enum 비의존을 위해 String으로.
    쓰기 금지(@Immutable) — 스키마는 이태연 레인 소유, cap은 읽기만.
*/
@Entity(name = "CapAnalysisLayerReference")
@Table(name = "analysis_layer")
@Immutable
@Getter
@NoArgsConstructor
public class CapAnalysisLayerReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "status")
    private String status;
}
