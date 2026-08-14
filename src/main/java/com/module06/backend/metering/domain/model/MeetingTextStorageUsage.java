package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 회의 하나가 지금 차지하고 있는 자막·요약(caption_chunk+transcript_chunk+meeting_summary) 저장
 * 용량의 스냅샷. 세 producer(cap의 caption_chunk, capture의 transcript_chunk·meeting_summary)가
 * 같은 회의에 각자 독립적으로 리포트하므로, 소스별로 바이트·revision을 따로 들고 있다 — 컬럼 하나로
 * 합치면 나중에 리포트하는 쪽이 앞선 소스의 값을 통째로 덮어써 총합이 아니라 "마지막 리포트 값"이
 * 되어버린다(자세한 이유는 meeting_text_storage_usage 마이그레이션 V4.9 주석 참고).
 *
 * revision도 소스별로 독립이다 — 공유하면 한 소스의 리포트가 다른 소스의 컬럼까지 "최신"으로
 * 오판하게 만든다. 각 소스의 revision은 호출 시점 System.currentTimeMillis()다(음성처럼 고정
 * 상수가 아니다 — 같은 소스가 같은 회의에 여러 번 리포트되므로 매번 실제로 더 큰 값이 보장돼야
 * 순서 역전을 막을 수 있다).
 */
public class MeetingTextStorageUsage {

    private final Long meetingId;
    private final Long companyId;
    private final Long projectId;
    private final long captionBytes;
    private final long captionRevision;
    private final long transcriptBytes;
    private final long transcriptRevision;
    private final long summaryBytes;
    private final long summaryRevision;
    private final LocalDateTime updatedAt;

    private MeetingTextStorageUsage(Long meetingId, Long companyId, Long projectId,
                                    long captionBytes, long captionRevision,
                                    long transcriptBytes, long transcriptRevision,
                                    long summaryBytes, long summaryRevision,
                                    LocalDateTime updatedAt) {
        this.meetingId = Objects.requireNonNull(meetingId, "meetingId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        if (captionBytes < 0 || captionRevision < 0 || transcriptBytes < 0 || transcriptRevision < 0
                || summaryBytes < 0 || summaryRevision < 0) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_RECORD_COMMAND_INVALID);
        }
        this.captionBytes = captionBytes;
        this.captionRevision = captionRevision;
        this.transcriptBytes = transcriptBytes;
        this.transcriptRevision = transcriptRevision;
        this.summaryBytes = summaryBytes;
        this.summaryRevision = summaryRevision;
        this.updatedAt = updatedAt;
    }

    // 이 회의의 첫 리포트 — 저장된 스냅샷이 아직 없을 때. 리포트한 소스만 채우고 나머지 둘은 0으로 시작한다.
    public static MeetingTextStorageUsage firstReport(Long meetingId, Long companyId, Long projectId,
                                                       TextStorageSource source, long usedBytes, long revision,
                                                       LocalDateTime updatedAt) {
        return switch (source) {
            case CAPTION -> new MeetingTextStorageUsage(meetingId, companyId, projectId,
                    usedBytes, revision, 0L, 0L, 0L, 0L, updatedAt);
            case TRANSCRIPT -> new MeetingTextStorageUsage(meetingId, companyId, projectId,
                    0L, 0L, usedBytes, revision, 0L, 0L, updatedAt);
            case SUMMARY -> new MeetingTextStorageUsage(meetingId, companyId, projectId,
                    0L, 0L, 0L, 0L, usedBytes, revision, updatedAt);
        };
    }

    // DB에서 읽어온 값으로 복원.
    public static MeetingTextStorageUsage restore(Long meetingId, Long companyId, Long projectId,
                                                   long captionBytes, long captionRevision,
                                                   long transcriptBytes, long transcriptRevision,
                                                   long summaryBytes, long summaryRevision,
                                                   LocalDateTime updatedAt) {
        return new MeetingTextStorageUsage(meetingId, companyId, projectId, captionBytes, captionRevision,
                transcriptBytes, transcriptRevision, summaryBytes, summaryRevision, updatedAt);
    }

    /**
     * 이 소스의 새 리포트를 기존 스냅샷에 병합한다. 그 소스의 revision이 기존 저장값보다 클 때만
     * 그 컬럼(바이트+revision)을 갱신한 새 스냅샷을 돌려주고, 나머지 두 소스는 그대로 보존한다.
     * 최신이 아니면(뒤바뀐 순서·중복) 자기 자신(this)을 그대로 돌려준다 — 호출자는 참조 동일성으로
     * "반영됐는지"를 구분할 수 있다.
     */
    public MeetingTextStorageUsage withSourceReportIfNewer(TextStorageSource source, long usedBytes, long revision,
                                                            LocalDateTime reportedAt) {
        return switch (source) {
            case CAPTION -> revision <= captionRevision ? this
                    : new MeetingTextStorageUsage(meetingId, companyId, projectId, usedBytes, revision,
                            transcriptBytes, transcriptRevision, summaryBytes, summaryRevision, reportedAt);
            case TRANSCRIPT -> revision <= transcriptRevision ? this
                    : new MeetingTextStorageUsage(meetingId, companyId, projectId, captionBytes, captionRevision,
                            usedBytes, revision, summaryBytes, summaryRevision, reportedAt);
            case SUMMARY -> revision <= summaryRevision ? this
                    : new MeetingTextStorageUsage(meetingId, companyId, projectId, captionBytes, captionRevision,
                            transcriptBytes, transcriptRevision, usedBytes, revision, reportedAt);
        };
    }

    /** 세 소스를 합친, 이 회의가 현재 차지하는 자막·요약 총 바이트. */
    public long getTotalUsedBytes() {
        return captionBytes + transcriptBytes + summaryBytes;
    }

    public Long getMeetingId() {
        return meetingId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public long getCaptionBytes() {
        return captionBytes;
    }

    public long getCaptionRevision() {
        return captionRevision;
    }

    public long getTranscriptBytes() {
        return transcriptBytes;
    }

    public long getTranscriptRevision() {
        return transcriptRevision;
    }

    public long getSummaryBytes() {
        return summaryBytes;
    }

    public long getSummaryRevision() {
        return summaryRevision;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
