package com.module06.backend.metering.domain.repository;

import java.time.LocalDateTime;

public interface BillingUsageQueryRepository {

    long sumRecordingBytes(Long companyId);

    long sumCaptionAndSummaryBytes(Long companyId);

    long countMeetings(Long companyId, LocalDateTime startInclusive, LocalDateTime endExclusive);
}
