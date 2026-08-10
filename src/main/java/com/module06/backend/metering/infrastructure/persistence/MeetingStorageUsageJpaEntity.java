package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// meetingId가 그대로 PK다(auto-increment 없음) — save()가 기존 행이 있으면 UPDATE, 없으면 INSERT를
// 알아서 처리한다(JPA merge 시맨틱, 수동 배정 식별자 엔티티의 표준 upsert 패턴).
@Entity
@Table(name = "meeting_storage_usage")
public class MeetingStorageUsageJpaEntity {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "used_bytes", nullable = false)
    private long usedBytes;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MeetingStorageUsageJpaEntity() {
    }

    private MeetingStorageUsageJpaEntity(Long meetingId, Long companyId, long usedBytes, LocalDateTime updatedAt) {
        this.meetingId = meetingId;
        this.companyId = companyId;
        this.usedBytes = usedBytes;
        this.updatedAt = updatedAt;
    }

    static MeetingStorageUsageJpaEntity from(MeetingStorageUsage usage) {
        return new MeetingStorageUsageJpaEntity(usage.getMeetingId(), usage.getCompanyId(), usage.getUsedBytes(),
                usage.getUpdatedAt());
    }

    MeetingStorageUsage toDomain() {
        return MeetingStorageUsage.restore(meetingId, companyId, usedBytes, updatedAt);
    }
}
