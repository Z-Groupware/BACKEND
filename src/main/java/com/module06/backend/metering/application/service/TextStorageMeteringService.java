package com.module06.backend.metering.application.service;

import com.module06.backend.metering.application.command.ReportMeetingTextStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingTextStorageUsagePort;
import com.module06.backend.metering.domain.repository.MeetingTextStorageUsageRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

// StorageMeteringService(음성)와 같은 패턴 — @Transactional을 두지 않는다. 실제 잠금·병합 트랜잭션은
// MeetingTextStorageUsagePersistenceAdapter.reportIfNewer 안(Writer)에서 처리한다.
@Service
public class TextStorageMeteringService implements ReportMeetingTextStorageUsagePort {

    private final MeetingTextStorageUsageRepository meetingTextStorageUsageRepository;
    // 다른 미터링 서비스와 동일한 KST 기준 Clock을 재사용한다.
    private final Clock clock;

    public TextStorageMeteringService(MeetingTextStorageUsageRepository meetingTextStorageUsageRepository,
                                      @Qualifier("meetingClock") Clock clock) {
        this.meetingTextStorageUsageRepository = meetingTextStorageUsageRepository;
        this.clock = clock;
    }

    @Override
    public void report(ReportMeetingTextStorageUsageCommand command) {
        meetingTextStorageUsageRepository.reportIfNewer(command.meetingId(), command.companyId(),
                command.projectId(), command.source(), command.usedBytes(), command.revision(),
                LocalDateTime.now(clock));
    }
}
