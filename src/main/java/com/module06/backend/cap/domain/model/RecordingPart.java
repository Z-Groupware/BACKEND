package com.module06.backend.cap.domain.model;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.global.exception.BusinessException;

import java.time.LocalDateTime;

/**
 * 청크 개별 업로드 기록. append-only — 한 번 저장되면 내용이 바뀌지 않는다(멱등성은
 * meeting_id/segment_seq/seq UNIQUE 제약이 물리적으로 보장).
 *
 * 저장 대상 테이블은 이태연 레인의 recording_part(V5.1)다. CAP-07 완료 통보 경로가 이 테이블의
 * 유일한 writer이며, 이 경로로 만들어지는 행은 항상 status=UPLOADED다(presign 단계에서 PRESIGNED
 * 행을 미리 만들지는 않는다). content_type은 recording_part.content_type(NOT NULL)을 채운다.
 */
public class RecordingPart {

    public static final long MAX_SIZE_BYTES = 2L * 1024 * 1024; // 청크당 크기 상한 2MB

    private final Long id;
    private final Long meetingId;
    private final int segmentSeq;
    private final int seq;
    private final String s3Key;
    private final String contentType;
    private final long sizeBytes;
    private final Long uploaderMemberId;
    private final RecordingPartStatus status;
    private final LocalDateTime uploadedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private RecordingPart(Long id, Long meetingId, int segmentSeq, int seq, String s3Key, String contentType,
                          long sizeBytes, Long uploaderMemberId, RecordingPartStatus status,
                          LocalDateTime uploadedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        requireId(meetingId, "meetingId");
        requireText(s3Key, "s3Key");
        requireText(contentType, "contentType");
        if (sizeBytes < 0) {
            throw new BusinessException(CapErrorCode.CAP_INVALID_PART_SIZE);
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new BusinessException(CapErrorCode.CAP_PART_SIZE_EXCEEDED);
        }
        this.id = id;
        this.meetingId = meetingId;
        this.segmentSeq = segmentSeq;
        this.seq = seq;
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploaderMemberId = uploaderMemberId;
        this.status = status;
        this.uploadedAt = uploadedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 신규 생성 — complete()에서 새로 완료 통보된 청크 하나를 기록할 때 사용.
    // 이 경로로 만들어지는 행은 실제 업로드가 확인된 것이므로 status=UPLOADED, uploaded_at=지금.
    public static RecordingPart create(Long meetingId, int segmentSeq, int seq, String s3Key, String contentType,
                                       long sizeBytes, Long uploaderMemberId) {
        return new RecordingPart(null, meetingId, segmentSeq, seq, s3Key, contentType, sizeBytes,
                uploaderMemberId, RecordingPartStatus.UPLOADED, LocalDateTime.now(), null, null);
    }

    // DB에서 읽어온 값으로 복원 (JPA 엔티티 → 도메인 모델)
    public static RecordingPart restore(Long id, Long meetingId, int segmentSeq, int seq, String s3Key,
                                        String contentType, long sizeBytes, Long uploaderMemberId,
                                        RecordingPartStatus status, LocalDateTime uploadedAt,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new RecordingPart(id, meetingId, segmentSeq, seq, s3Key, contentType, sizeBytes, uploaderMemberId,
                status, uploadedAt, createdAt, updatedAt);
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
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Long getUploaderMemberId() { return uploaderMemberId; }
    public RecordingPartStatus getStatus() { return status; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
