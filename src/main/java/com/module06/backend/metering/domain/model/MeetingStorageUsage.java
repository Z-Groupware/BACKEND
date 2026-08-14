package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 회의 하나가 지금 차지하고 있는 저장 용량의 스냅샷. TokenUsageRecord(append-only 이벤트 로그)와
 * 달리 회의당 딱 한 행만 있고, cap이 값이 바뀔 때마다(청크 완료·조립 완료·삭제) 그 시점의 총량으로
 * 덮어쓴다(meetingId가 곧 식별자).
 *
 * revision(호출자가 매기는 단조 증가 순번)이 기존 저장값보다 클 때만 반영된다 — 서버 수신 시각으로
 * 순서를 판단하면, 네트워크 지연으로 report가 뒤바뀐 순서로 도착했을 때 최신 값을 오래된 값이
 * 덮어써 사용량이 과소 집계될 수 있다(CodeRabbit 지적). 반영 여부 판정은
 * MeetingStorageUsageRepository.reportIfNewer가 담당한다.
 *
 * 회사 전체 사용량은 이 스냅샷들의 SUM이다 — 회의 수만큼만 행이 있어서, 회의당 청크가 아무리
 * 쌓여도(수백~수천 개) 조회 비용이 늘지 않는다.
 */
public class MeetingStorageUsage {

    private final Long meetingId;
    private final Long companyId;
    private final Long projectId;
    private final long usedBytes;
    private final long revision;
    private final LocalDateTime updatedAt;

    private MeetingStorageUsage(Long meetingId, Long companyId, Long projectId, long usedBytes, long revision,
                                LocalDateTime updatedAt) {
        this.meetingId = Objects.requireNonNull(meetingId, "meetingId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        if (usedBytes < 0 || revision < 0) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_RECORD_COMMAND_INVALID);
        }
        this.usedBytes = usedBytes;
        this.revision = revision;
        this.updatedAt = updatedAt;
    }

    // 신규 report — cap이 이 회의의 현재 용량을 보고할 때. updatedAt은 서비스가 Clock으로 채운다.
    public static MeetingStorageUsage report(Long meetingId, Long companyId, Long projectId, long usedBytes,
                                             long revision, LocalDateTime updatedAt) {
        return new MeetingStorageUsage(meetingId, companyId, projectId, usedBytes, revision, updatedAt);
    }

    // DB에서 읽어온 값으로 복원.
    public static MeetingStorageUsage restore(Long meetingId, Long companyId, Long projectId, long usedBytes,
                                              long revision, LocalDateTime updatedAt) {
        return new MeetingStorageUsage(meetingId, companyId, projectId, usedBytes, revision, updatedAt);
    }

    /** 이 report가 저장된 상태보다 최신인지(revision이 더 큰지) — 같거나 오래되면 무시해야 한다. */
    public boolean isNewerThan(MeetingStorageUsage stored) {
        return stored == null || this.revision > stored.revision;
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

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getRevision() {
        return revision;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
