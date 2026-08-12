package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.model.RecordingPartStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// recording_part 테이블(이태연 V5.1)의 실제 JPA 매핑. 도메인 모델(RecordingPart)과 DB 컬럼을 이어준다.
// V5.1 스키마의 NOT NULL 컬럼(uploader_member_id, content_type, size_bytes, status)을 모두 채운다.
// capture_session_id / start_offset_ms / duration_ms 는 nullable이라 이 API 경로에선 매핑하지 않는다.
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

    @Column(name = "uploader_member_id", nullable = false)
    private Long uploaderMemberId;

    @Column(name = "s3_key", length = 1024)
    private String s3Key;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordingPartStatus status;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

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
        entity.uploaderMemberId = part.getUploaderMemberId();
        entity.s3Key = part.getS3Key();
        entity.contentType = part.getContentType();
        entity.sizeBytes = part.getSizeBytes();
        entity.status = part.getStatus();
        entity.uploadedAt = part.getUploadedAt();
        return entity;
    }

    // JPA 엔티티 → 도메인 모델 (DB에서 읽어온 직후)
    RecordingPart toDomain() {
        return RecordingPart.restore(id, meetingId, segmentSeq, seq, s3Key, contentType, sizeBytes,
                uploaderMemberId, status, uploadedAt, createdAt, updatedAt);
    }
}
