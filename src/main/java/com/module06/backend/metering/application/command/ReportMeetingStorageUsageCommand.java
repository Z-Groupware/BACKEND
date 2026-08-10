package com.module06.backend.metering.application.command;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

/**
 * cap이 회의 하나의 현재 저장 용량 스냅샷을 보고하는 요청. usedBytes는 그 순간 이 회의가 실제로
 * 차지하는 총 바이트(청크 누적합 또는 최종 조립본 크기) — 델타가 아니라 절댓값이라 멱등하다.
 */
public record ReportMeetingStorageUsageCommand(Long companyId, Long meetingId, long usedBytes) {

    public ReportMeetingStorageUsageCommand {
        if (companyId == null || meetingId == null || usedBytes < 0) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_RECORD_COMMAND_INVALID);
        }
    }
}
