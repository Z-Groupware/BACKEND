package com.module06.backend.metering.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 회의 하나가 지금 차지하고 있는 저장 용량의 스냅샷. TokenUsageRecord(append-only 이벤트 로그)와
 * 달리 회의당 딱 한 행만 있고, cap이 값이 바뀔 때마다(청크 완료·조립 완료·삭제) 그 시점의 총량으로
 * 덮어쓴다(meetingId가 곧 식별자 — 같은 값을 다시 report해도 안전하게 멱등하다).
 *
 * 회사 전체 사용량은 이 스냅샷들의 SUM이다 — 회의 수만큼만 행이 있어서, 회의당 청크가 아무리
 * 쌓여도(수백~수천 개) 조회 비용이 늘지 않는다(TokenUsageRecord처럼 이벤트마다 행이 느는 구조와
 * 다른 점).
 */
public class MeetingStorageUsage {

    private final Long meetingId;
    private final Long companyId;
    private final long usedBytes;
    private final LocalDateTime updatedAt;

    private MeetingStorageUsage(Long meetingId, Long companyId, long usedBytes, LocalDateTime updatedAt) {
        this.meetingId = Objects.requireNonNull(meetingId, "meetingId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        if (usedBytes < 0) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_RECORD_COMMAND_INVALID);
        }
        this.usedBytes = usedBytes;
        this.updatedAt = updatedAt;
    }

    // 신규 report — cap이 이 회의의 현재 용량을 보고할 때. updatedAt은 서비스가 Clock으로 채운다.
    public static MeetingStorageUsage report(Long meetingId, Long companyId, long usedBytes,
                                             LocalDateTime updatedAt) {
        return new MeetingStorageUsage(meetingId, companyId, usedBytes, updatedAt);
    }

    // DB에서 읽어온 값으로 복원.
    public static MeetingStorageUsage restore(Long meetingId, Long companyId, long usedBytes,
                                              LocalDateTime updatedAt) {
        return new MeetingStorageUsage(meetingId, companyId, usedBytes, updatedAt);
    }

    public Long getMeetingId() {
        return meetingId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
