package com.module06.backend.cap.domain.model;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.global.exception.BusinessException;

import java.time.LocalDateTime;

/**
 * 청크 개별 업로드 기록. append-only — 한 번 저장되면 내용이 바뀌지 않는다(멱등성은
 * meeting_id/segment_seq/seq UNIQUE 제약이 물리적으로 보장).
 */
public class RecordingPart {

    public static final long MAX_SIZE_BYTES = 2L * 1024 * 1024; // 청크당 크기 상한 2MB

    private final Long id;
    private final Long meetingId;
    private final int segmentSeq;
    private final int seq;
    private final String s3Key;
    private final long sizeBytes;
    private final Long uploadedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private RecordingPart(Long id, Long meetingId, int segmentSeq, int seq, String s3Key, long sizeBytes,
                          Long uploadedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        requireId(meetingId, "meetingId");
        requireText(s3Key, "s3Key");
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new BusinessException(CapErrorCode.CAP_PART_SIZE_EXCEEDED);
        }
        this.id = id;
        this.meetingId = meetingId;
        this.segmentSeq = segmentSeq;
        this.seq = seq;
        this.s3Key = s3Key;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 신규 생성 — complete()에서 새로 완료 통보된 청크 하나를 기록할 때 사용
    public static RecordingPart create(Long meetingId, int segmentSeq, int seq, String s3Key, long sizeBytes,
                                       Long uploadedBy) {
        return new RecordingPart(null, meetingId, segmentSeq, seq, s3Key, sizeBytes, uploadedBy, null, null);
    }

    // DB에서 읽어온 값으로 복원 (JPA 엔티티 → 도메인 모델)
    public static RecordingPart restore(Long id, Long meetingId, int segmentSeq, int seq, String s3Key,
                                        long sizeBytes, Long uploadedBy, LocalDateTime createdAt,
                                        LocalDateTime updatedAt) {
        return new RecordingPart(id, meetingId, segmentSeq, seq, s3Key, sizeBytes, uploadedBy, createdAt, updatedAt);
    }

    private static void requireId(Long value, String name) {
        if (value == null) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_ID);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_TEXT);
        }
    }

    public Long getId() { return id; }
    public Long getMeetingId() { return meetingId; }
    public int getSegmentSeq() { return segmentSeq; }
    public int getSeq() { return seq; }
    public String getS3Key() { return s3Key; }
    public long getSizeBytes() { return sizeBytes; }
    public Long getUploadedBy() { return uploadedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
