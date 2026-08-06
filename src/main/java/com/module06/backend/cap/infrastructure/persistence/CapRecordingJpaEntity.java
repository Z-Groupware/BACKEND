package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.Recording;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// recording 테이블(V1) JPA 매핑. 도메인 모델(Recording)과 DB 컬럼을 이어준다.
// ⚠️ Cap 접두어: 다른 도메인이 같은 recording 테이블을 매핑할 때 엔티티명이 겹치지 않도록 분리(도메인 프리픽스 규칙).
// UNIQUE(meeting_id)는 V4.2.1 마이그레이션이 실 DB에 건다 — 엔티티에도 명시해 "회의당 1녹음" 불변식을
// 문서화하고, Flyway 없이 뜨는 테스트(H2)에서도 동일 제약이 걸리게 한다.
@Entity(name = "CapRecording")
@Table(name = "recording",
        uniqueConstraints = @UniqueConstraint(name = "UK_RECORDING_MEETING", columnNames = "meeting_id"))
public class CapRecordingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CapRecordingJpaEntity() {
    }

    // 도메인 모델 → JPA 엔티티 (저장 직전)
    static CapRecordingJpaEntity fromDomain(Recording recording) {
        CapRecordingJpaEntity entity = new CapRecordingJpaEntity();
        entity.id = recording.getId();
        entity.meetingId = recording.getMeetingId();
        entity.fileName = recording.getFileName();
        entity.fileUrl = recording.getFileUrl();
        entity.fileSize = recording.getSizeBytes();
        entity.durationSec = recording.getDurationSec();
        return entity;
    }

    // JPA 엔티티 → 도메인 모델 (DB에서 읽어온 직후)
    Recording toDomain() {
        return Recording.restore(id, meetingId, fileName, fileUrl, fileSize, durationSec, createdAt, updatedAt);
    }
}
