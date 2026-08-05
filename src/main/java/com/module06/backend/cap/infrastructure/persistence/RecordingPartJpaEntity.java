package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.RecordingPart;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// recording_part 테이블의 실제 JPA 매핑. 도메인 모델(RecordingPart)과 DB 컬럼을 이어주는 역할만 한다.
@Entity
@Table(name = "recording_part")
public class RecordingPartJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "segment_seq", nullable = false)
    private Integer segmentSeq;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected RecordingPartJpaEntity() {
    }

    // 도메인 모델 → JPA 엔티티 (저장 직전)
    static RecordingPartJpaEntity fromDomain(RecordingPart part) {
        RecordingPartJpaEntity entity = new RecordingPartJpaEntity();
        entity.id = part.getId();
        entity.meetingId = part.getMeetingId();
        entity.segmentSeq = part.getSegmentSeq();
        entity.seq = part.getSeq();
        entity.s3Key = part.getS3Key();
        entity.sizeBytes = part.getSizeBytes();
        entity.uploadedBy = part.getUploadedBy();
        return entity;
    }

    // JPA 엔티티 → 도메인 모델 (DB에서 읽어온 직후)
    RecordingPart toDomain() {
        return RecordingPart.restore(id, meetingId, segmentSeq, seq, s3Key, sizeBytes, uploadedBy,
                createdAt, updatedAt);
    }
}
