package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.MeetingTextStorageUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// meetingId가 그대로 PK다(auto-increment 없음) — MeetingStorageUsageJpaEntity와 동일한 수동 배정
// 식별자 upsert 패턴. 소스(캡션/transcript/요약)별 바이트·revision을 각각 컬럼으로 둔다 — 이유는
// MeetingTextStorageUsage 클래스 주석 참고.
@Entity
@Table(name = "meeting_text_storage_usage")
public class MeetingTextStorageUsageJpaEntity {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "caption_bytes", nullable = false)
    private long captionBytes;

    @Column(name = "caption_revision", nullable = false)
    private long captionRevision;

    @Column(name = "transcript_bytes", nullable = false)
    private long transcriptBytes;

    @Column(name = "transcript_revision", nullable = false)
    private long transcriptRevision;

    @Column(name = "summary_bytes", nullable = false)
    private long summaryBytes;

    @Column(name = "summary_revision", nullable = false)
    private long summaryRevision;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MeetingTextStorageUsageJpaEntity() {
    }

    private MeetingTextStorageUsageJpaEntity(Long meetingId, Long companyId, Long projectId,
                                             long captionBytes, long captionRevision,
                                             long transcriptBytes, long transcriptRevision,
                                             long summaryBytes, long summaryRevision,
                                             LocalDateTime updatedAt) {
        this.meetingId = meetingId;
        this.companyId = companyId;
        this.projectId = projectId;
        this.captionBytes = captionBytes;
        this.captionRevision = captionRevision;
        this.transcriptBytes = transcriptBytes;
        this.transcriptRevision = transcriptRevision;
        this.summaryBytes = summaryBytes;
        this.summaryRevision = summaryRevision;
        this.updatedAt = updatedAt;
    }

    static MeetingTextStorageUsageJpaEntity from(MeetingTextStorageUsage usage) {
        return new MeetingTextStorageUsageJpaEntity(usage.getMeetingId(), usage.getCompanyId(),
                usage.getProjectId(), usage.getCaptionBytes(), usage.getCaptionRevision(),
                usage.getTranscriptBytes(), usage.getTranscriptRevision(), usage.getSummaryBytes(),
                usage.getSummaryRevision(), usage.getUpdatedAt());
    }

    MeetingTextStorageUsage toDomain() {
        return MeetingTextStorageUsage.restore(meetingId, companyId, projectId, captionBytes, captionRevision,
                transcriptBytes, transcriptRevision, summaryBytes, summaryRevision, updatedAt);
    }
}
