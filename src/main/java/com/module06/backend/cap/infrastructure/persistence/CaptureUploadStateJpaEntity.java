package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// capture_upload_state 테이블의 실제 JPA 매핑. 도메인 모델(CaptureUploadState)과 DB 컬럼을 이어주는 역할만 한다.
@Entity
@Table(name = "capture_upload_state")
public class CaptureUploadStateJpaEntity {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "segment_seq", nullable = false)
    private Integer segmentSeq;

    @Column(name = "recorder_person_id")
    private Long recorderPersonId;

    @Column(name = "last_seq", nullable = false)
    private Integer lastSeq;

    @Column(name = "blocks_formed", nullable = false)
    private Integer blocksFormed;

    @Column(name = "last_block_end_offset_ms", nullable = false)
    private Integer lastBlockEndOffsetMs;

    @Column(name = "total_bytes_uploaded", nullable = false)
    private long totalBytesUploaded;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CaptureUploadStateJpaEntity() {
    }

    // 도메인 모델 → JPA 엔티티 (저장 직전)
    static CaptureUploadStateJpaEntity fromDomain(CaptureUploadState state) {
        CaptureUploadStateJpaEntity entity = new CaptureUploadStateJpaEntity();
        entity.meetingId = state.getMeetingId();
        entity.segmentSeq = state.getSegmentSeq();
        entity.recorderPersonId = state.getRecorderPersonId();
        entity.lastSeq = state.getLastSeq();
        entity.blocksFormed = state.getBlocksFormed();
        entity.lastBlockEndOffsetMs = Math.toIntExact(state.getLastBlockEndOffsetMs());
        entity.totalBytesUploaded = state.getTotalBytesUploaded();
        return entity;
    }

    // JPA 엔티티 → 도메인 모델 (DB에서 읽어온 직후)
    CaptureUploadState toDomain() {
        return CaptureUploadState.restore(meetingId, segmentSeq, recorderPersonId, lastSeq, blocksFormed,
                lastBlockEndOffsetMs, totalBytesUploaded, createdAt, updatedAt);
    }
}
