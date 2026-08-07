package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.CaptionChunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// caption_chunk(V5.2) 쓰기용 매핑. 이 테이블의 주인은 cap(CAP-11이 쓴다) — capture는 읽기 전용
// CaptionChunkJpaEntity(다른 클래스명)로 화자 귀속 판정에만 쓴다.
// ⚠️ Cap 접두어: capture 쪽 엔티티와 클래스명이 달라 지금 당장 충돌은 없지만, meeting/recording 등
// 다른 공용 테이블에서 이미 겹침 사고가 있었던 팀 컨벤션(Cap 접두어 필수)을 그대로 따른다.
// UNIQUE(meeting_id, member_id, seq)는 V5.2 마이그레이션이 실 DB에 건다 — 엔티티에도 명시해
// "재전송 멱등 키" 불변식을 문서화하고, Flyway 없이 뜨는 테스트(H2)에서도 동일 제약이 걸리게 한다
// (CapRecordingJpaEntity의 UK_RECORDING_MEETING과 동일 패턴).
@Entity(name = "CapCaptionChunk")
@Table(name = "caption_chunk",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_CAPTION_CHUNK_MEETING_MEMBER_SEQ", columnNames = {"meeting_id", "member_id", "seq"}))
public class CapCaptionChunkJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "start_offset_ms", nullable = false)
    private int startOffsetMs;

    @Column(name = "end_offset_ms", nullable = false)
    private int endOffsetMs;

    // 마이그레이션(V5.2)의 VARCHAR(1000)과 길이를 맞춘다 — 없으면 Hibernate 기본값(255)로 매핑돼
    // H2 테스트 스키마가 실 DB보다 짧아진다(create-drop 환경에서만 드러나는 불일치).
    @Column(name = "text", nullable = false, length = 1000)
    private String text;

    @Column(name = "rms", nullable = false, precision = 6, scale = 2)
    private BigDecimal rms;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CapCaptionChunkJpaEntity() {
    }

    // 도메인 모델 → JPA 엔티티 (저장 직전)
    static CapCaptionChunkJpaEntity fromDomain(CaptionChunk chunk) {
        CapCaptionChunkJpaEntity entity = new CapCaptionChunkJpaEntity();
        entity.id = chunk.getId();
        entity.meetingId = chunk.getMeetingId();
        entity.memberId = chunk.getMemberId();
        entity.seq = chunk.getSeq();
        entity.startOffsetMs = chunk.getStartOffsetMs();
        entity.endOffsetMs = chunk.getEndOffsetMs();
        entity.text = chunk.getText();
        entity.rms = chunk.getRms();
        return entity;
    }

    // JPA 엔티티 → 도메인 모델 (DB에서 읽어온 직후)
    CaptionChunk toDomain() {
        return CaptionChunk.restore(id, meetingId, memberId, seq, startOffsetMs, endOffsetMs, text, rms,
                createdAt, updatedAt);
    }
}
